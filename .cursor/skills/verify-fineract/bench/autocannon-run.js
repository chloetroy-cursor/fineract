#!/usr/bin/env node
// One autocannon run against a running Fineract. Prints one JSON object on stdout.
//
//   node autocannon-run.js --workload read|repay --first-loan ID --loans N [--connections 8] [--duration 30]
//
// Requests go round-robin over loans first-loan .. first-loan+N-1 from one shared counter, so no two
// in-flight requests touch the same loan as long as N is larger than the number of connections.
// The exit code is 1 when any response was not 2xx, when autocannon saw an error or a timeout, or when the
// repayments cannot be found on the loans afterwards.
'use strict'

const autocannon = require('autocannon')
const https = require('https')

const BASE_URL = process.env.FINERACT_URL || 'https://localhost:8443/fineract-provider'
const HEADERS = {
  'Content-Type': 'application/json',
  'Fineract-Platform-TenantId': 'default',
  Authorization: 'Basic ' + Buffer.from('mifos:password').toString('base64')
}
const REPAYMENT_AMOUNT = 10
const REPAYMENT_BODY = JSON.stringify({
  transactionDate: '01 February 2026',
  transactionAmount: REPAYMENT_AMOUNT,
  dateFormat: 'dd MMMM yyyy',
  locale: 'en'
})

function parseArgs (argv) {
  const args = { connections: 8, duration: 30 }
  for (let i = 2; i < argv.length; i += 2) {
    args[argv[i].replace(/^--/, '').replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = argv[i + 1]
  }
  for (const key of ['connections', 'duration', 'firstLoan', 'loans']) {
    args[key] = Number(args[key])
    if (!Number.isInteger(args[key]) || args[key] <= 0) {
      throw new Error(`--${key} must be a positive integer`)
    }
  }
  if (!['read', 'repay'].includes(args.workload)) {
    throw new Error('--workload must be read or repay')
  }
  if (args.loans <= args.connections) {
    throw new Error('--loans must be larger than --connections')
  }
  return args
}

function getLoan (loanId) {
  return new Promise((resolve, reject) => {
    const url = new URL(`${BASE_URL}/api/v1/loans/${loanId}`)
    https.get(url, { headers: HEADERS, rejectUnauthorized: false }, (res) => {
      let body = ''
      res.on('data', (chunk) => { body += chunk })
      res.on('end', () => {
        if (res.statusCode !== 200) {
          reject(new Error(`GET loans/${loanId} answered ${res.statusCode}: ${body.slice(0, 200)}`))
        } else {
          resolve(JSON.parse(body))
        }
      })
    }).on('error', reject)
  })
}

async function totalRepaid (loanId) {
  const loan = await getLoan(loanId)
  return (loan.summary && loan.summary.totalRepayment) || 0
}

// Exact percentile (nearest rank) over the response times autocannon reports for each response.
// autocannon's own histogram rounds to whole milliseconds, which is too coarse for a few-percent comparison.
function percentile (sorted, p) {
  if (sorted.length === 0) return null
  const rank = Math.ceil((p / 100) * sorted.length)
  return sorted[Math.min(sorted.length, Math.max(1, rank)) - 1]
}

function round (value, digits = 3) {
  return value === null ? null : Number(value.toFixed(digits))
}

async function main () {
  const args = parseArgs(process.argv)
  const base = new URL(BASE_URL)
  const apiPath = `${base.pathname.replace(/\/$/, '')}/api/v1`
  const sampleCount = Math.min(args.loans, 25)
  const sampleOffsets = Array.from({ length: sampleCount }, (_, i) => Math.floor((i * args.loans) / sampleCount))

  const first = await getLoan(args.firstLoan)
  if (first.id !== args.firstLoan || !first.status || first.status.active !== true) {
    throw new Error(`loan ${args.firstLoan} is not an active loan`)
  }
  const repaidBefore = args.workload === 'repay'
    ? await Promise.all(sampleOffsets.map((offset) => totalRepaid(args.firstLoan + offset)))
    : []

  let sent = 0
  const sentPerOffset = new Map(sampleOffsets.map((offset) => [offset, 0]))
  const responseTimes = []
  const statusCounts = {}

  const request = {
    method: args.workload === 'read' ? 'GET' : 'POST',
    path: `${apiPath}/loans/${args.firstLoan}`,
    setupRequest: (req) => {
      const offset = sent % args.loans
      sent += 1
      if (sentPerOffset.has(offset)) {
        sentPerOffset.set(offset, sentPerOffset.get(offset) + 1)
      }
      const loanId = args.firstLoan + offset
      req.path = args.workload === 'read'
        ? `${apiPath}/loans/${loanId}`
        : `${apiPath}/loans/${loanId}/transactions?command=repayment`
      return req
    }
  }
  if (args.workload === 'repay') {
    request.body = REPAYMENT_BODY
  }

  const instance = autocannon({
    url: base.origin,
    connections: args.connections,
    duration: args.duration,
    pipelining: 1,
    timeout: 30,
    headers: HEADERS,
    tlsOptions: { rejectUnauthorized: false },
    requests: [request]
  })
  instance.on('response', (client, statusCode, resBytes, responseTime) => {
    statusCounts[statusCode] = (statusCounts[statusCode] || 0) + 1
    if (statusCode >= 200 && statusCode < 300) {
      responseTimes.push(responseTime)
    }
  })
  const result = await instance

  const problems = []
  if (result.non2xx !== 0) problems.push(`${result.non2xx} responses were not 2xx`)
  if (result.errors !== 0) problems.push(`${result.errors} connection errors`)
  if (result.timeouts !== 0) problems.push(`${result.timeouts} timeouts`)
  if (result['2xx'] === 0) problems.push('no 2xx response')

  // A second, read-only view of the repayments: each sampled loan must have been repaid once for every request
  // sent to it. A request cut off when the run ended may be missing, so one fewer is accepted.
  const repaymentCheck = []
  if (args.workload === 'repay') {
    const repaidAfter = await Promise.all(sampleOffsets.map((offset) => totalRepaid(args.firstLoan + offset)))
    sampleOffsets.forEach((offset, i) => {
      const expected = sentPerOffset.get(offset)
      const found = Math.round((repaidAfter[i] - repaidBefore[i]) / REPAYMENT_AMOUNT)
      repaymentCheck.push({ loanId: args.firstLoan + offset, sent: expected, found })
      if (found > expected || found < expected - 1 || (expected > 1 && found === 0)) {
        problems.push(`loan ${args.firstLoan + offset}: ${expected} repayments sent, ${found} found`)
      }
    })
  }

  responseTimes.sort((a, b) => a - b)
  const elapsedSeconds = (result.finish - result.start) / 1000
  const output = {
    workload: args.workload,
    connections: args.connections,
    durationSeconds: args.duration,
    firstLoan: args.firstLoan,
    loans: args.loans,
    start: result.start,
    finish: result.finish,
    elapsedSeconds: round(elapsedSeconds),
    requestsSent: result.requests.sent,
    responses2xx: result['2xx'],
    non2xx: result.non2xx,
    errors: result.errors,
    timeouts: result.timeouts,
    statusCounts,
    // autocannon's figure: the mean of its once-a-second request counters
    requestsPerSecond: result.requests.average,
    requestsPerSecondStddev: result.requests.stddev,
    latencyMs: {
      mean: round(responseTimes.reduce((sum, v) => sum + v, 0) / (responseTimes.length || 1)),
      p50: round(percentile(responseTimes, 50)),
      p97_5: round(percentile(responseTimes, 97.5)),
      p99: round(percentile(responseTimes, 99)),
      max: round(percentile(responseTimes, 100))
    },
    autocannonLatencyMs: {
      mean: result.latency.average,
      p50: result.latency.p50,
      p97_5: result.latency.p97_5,
      p99: result.latency.p99,
      max: result.latency.max
    },
    repaymentCheck: {
      loansChecked: repaymentCheck.length,
      sent: repaymentCheck.reduce((sum, r) => sum + r.sent, 0),
      found: repaymentCheck.reduce((sum, r) => sum + r.found, 0)
    },
    problems
  }
  process.stdout.write(JSON.stringify(output) + '\n')
  process.exit(problems.length === 0 ? 0 : 1)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(2)
})
