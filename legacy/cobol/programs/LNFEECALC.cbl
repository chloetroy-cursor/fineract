       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNFEECALC.
      *================================================================*
      * LNFEECALC - Loan late fee calculator (end-of-day batch)        *
      *                                                                *
      * Demo legacy program. See legacy/cobol/SPEC.md for the rules.   *
      * Not customer source. Intended for COBOL -> Java migration      *
      * demos against Apache Fineract.                                 *
      *================================================================*
       ENVIRONMENT DIVISION.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY LNFEECTL.

       01  WS-CONSTANTS.
           05  WS-STATUS-OK                 PIC X(02) VALUE "00".
           05  WS-STATUS-GRACE              PIC X(02) VALUE "01".
           05  WS-STATUS-ZERO-PRIN          PIC X(02) VALUE "02".
           05  WS-STATUS-BAD-PROD           PIC X(02) VALUE "99".

       01  WS-WORK.
           05  WS-BILLABLE-DAYS             PIC S9(05) COMP-3.
           05  WS-TIER1-DAYS                PIC S9(05) COMP-3.
           05  WS-TIER2-DAYS                PIC S9(05) COMP-3.
           05  WS-TIER3-DAYS                PIC S9(05) COMP-3.
           05  WS-REMAINING                 PIC S9(05) COMP-3.
           05  WS-T2-SPAN                   PIC S9(05) COMP-3.
           05  WS-TIER1-FEE                 PIC S9(13)V9(6) COMP-3.
           05  WS-TIER2-FEE                 PIC S9(13)V9(6) COMP-3.
           05  WS-TIER3-FEE                 PIC S9(13)V9(6) COMP-3.
           05  WS-RAW-FEE                   PIC S9(13)V9(6) COMP-3.
           05  WS-CAP-AMT                   PIC S9(13)V9(6) COMP-3.
           05  WS-CAPPED                    PIC S9(13)V9(6) COMP-3.
           05  WS-ROUNDED                   PIC S9(13)V99 COMP-3.
           05  WS-FOUND-PRODUCT             PIC X(01) VALUE "N".
               88  PRODUCT-FOUND            VALUE "Y".
               88  PRODUCT-NOT-FOUND        VALUE "N".

       01  WS-PRODUCT-TABLE.
           05  WS-PROD-COUNT                PIC 9(02) VALUE 3.
           05  WS-PROD-ENTRY OCCURS 3 TIMES
                            INDEXED BY WS-PROD-IDX.
               10  WP-CODE                  PIC X(08).
               10  WP-GRACE                 PIC 9(03).
               10  WP-T1-MAX                PIC 9(03).
               10  WP-T2-MAX                PIC 9(03).
               10  WP-T1-BP                 PIC 9(04).
               10  WP-T2-BP                 PIC 9(04).
               10  WP-T3-BP                 PIC 9(04).
               10  WP-CAP-PCT               PIC 9(03).
               10  WP-MIN-FEE               PIC 9(07)V99.

       LINKAGE SECTION.
       COPY LNFEEREC.

       PROCEDURE DIVISION USING LN-FEE-RECORD.
       MAIN-SECTION.
           PERFORM INIT-OUTPUT
           PERFORM LOAD-PRODUCT-TABLE
           PERFORM LOOKUP-PRODUCT
           IF PRODUCT-NOT-FOUND
              MOVE WS-STATUS-BAD-PROD TO LN-STATUS-CODE
              MOVE "UNKNOWN PRODUCT CODE" TO LN-STATUS-MSG
              GOBACK
           END-IF
           IF LN-PRINCIPAL-OUTSTANDING = 0
              MOVE WS-STATUS-ZERO-PRIN TO LN-STATUS-CODE
              MOVE "ZERO PRINCIPAL - NO FEE" TO LN-STATUS-MSG
              GOBACK
           END-IF
           IF LN-DAYS-OVERDUE <= CTL-GRACE-DAYS
              MOVE WS-STATUS-GRACE TO LN-STATUS-CODE
              MOVE "WITHIN GRACE PERIOD" TO LN-STATUS-MSG
              MOVE 0 TO LN-BILLABLE-DAYS
              GOBACK
           END-IF
           PERFORM COMPUTE-BILLABLE-DAYS
           PERFORM SPLIT-TIERS
           PERFORM COMPUTE-RAW-FEE
           PERFORM APPLY-CAP
           PERFORM ROUND-HALF-UP
           PERFORM APPLY-MINIMUM
           MOVE WS-ROUNDED TO LN-LATE-FEE-AMT
           MOVE WS-BILLABLE-DAYS TO LN-BILLABLE-DAYS
           MOVE WS-STATUS-OK TO LN-STATUS-CODE
           MOVE "FEE ASSESSED" TO LN-STATUS-MSG
           GOBACK.

       INIT-OUTPUT.
           MOVE 0 TO LN-LATE-FEE-AMT
           MOVE 0 TO LN-BILLABLE-DAYS
           MOVE SPACES TO LN-STATUS-CODE
           MOVE SPACES TO LN-STATUS-MSG
           MOVE "N" TO WS-FOUND-PRODUCT.

       LOAD-PRODUCT-TABLE.
      *    EVERYDAY - retail everyday banking
           MOVE "EVERYDAY" TO WP-CODE      (1)
           MOVE 5          TO WP-GRACE     (1)
           MOVE 30         TO WP-T1-MAX    (1)
           MOVE 60         TO WP-T2-MAX    (1)
           MOVE 5          TO WP-T1-BP     (1)
           MOVE 8          TO WP-T2-BP     (1)
           MOVE 10         TO WP-T3-BP     (1)
           MOVE 5          TO WP-CAP-PCT   (1)
           MOVE 1.00       TO WP-MIN-FEE   (1)
      *    CONSUMO - consumer installment
           MOVE "CONSUMO " TO WP-CODE      (2)
           MOVE 3          TO WP-GRACE     (2)
           MOVE 30         TO WP-T1-MAX    (2)
           MOVE 60         TO WP-T2-MAX    (2)
           MOVE 6          TO WP-T1-BP     (2)
           MOVE 9          TO WP-T2-BP     (2)
           MOVE 12         TO WP-T3-BP     (2)
           MOVE 7          TO WP-CAP-PCT   (2)
           MOVE 2.00       TO WP-MIN-FEE   (2)
      *    HIPOTEC - mortgage / secured
           MOVE "HIPOTEC " TO WP-CODE      (3)
           MOVE 15         TO WP-GRACE     (3)
           MOVE 30         TO WP-T1-MAX    (3)
           MOVE 60         TO WP-T2-MAX    (3)
           MOVE 2          TO WP-T1-BP     (3)
           MOVE 3          TO WP-T2-BP     (3)
           MOVE 4          TO WP-T3-BP     (3)
           MOVE 2          TO WP-CAP-PCT   (3)
           MOVE 5.00       TO WP-MIN-FEE   (3).

       LOOKUP-PRODUCT.
           SET WS-PROD-IDX TO 1
           SEARCH WS-PROD-ENTRY
               AT PRODUCT-NOT-FOUND
               WHEN WP-CODE (WS-PROD-IDX) = LN-PRODUCT-CODE
                   MOVE "Y" TO WS-FOUND-PRODUCT
                   MOVE WP-CODE      (WS-PROD-IDX) TO CTL-PRODUCT-CODE
                   MOVE WP-GRACE     (WS-PROD-IDX) TO CTL-GRACE-DAYS
                   MOVE WP-T1-MAX    (WS-PROD-IDX) TO CTL-TIER1-MAX-DAYS
                   MOVE WP-T2-MAX    (WS-PROD-IDX) TO CTL-TIER2-MAX-DAYS
                   MOVE WP-T1-BP     (WS-PROD-IDX) TO CTL-TIER1-RATE-BP
                   MOVE WP-T2-BP     (WS-PROD-IDX) TO CTL-TIER2-RATE-BP
                   MOVE WP-T3-BP     (WS-PROD-IDX) TO CTL-TIER3-RATE-BP
                   MOVE WP-CAP-PCT   (WS-PROD-IDX) TO CTL-CAP-PCT
                   MOVE WP-MIN-FEE   (WS-PROD-IDX) TO CTL-MIN-FEE
           END-SEARCH.

       COMPUTE-BILLABLE-DAYS.
           COMPUTE WS-BILLABLE-DAYS =
               LN-DAYS-OVERDUE - CTL-GRACE-DAYS.

       SPLIT-TIERS.
           MOVE WS-BILLABLE-DAYS TO WS-REMAINING
           MOVE 0 TO WS-TIER1-DAYS
           MOVE 0 TO WS-TIER2-DAYS
           MOVE 0 TO WS-TIER3-DAYS
           COMPUTE WS-T2-SPAN =
               CTL-TIER2-MAX-DAYS - CTL-TIER1-MAX-DAYS
      *    Tier 1
           IF WS-REMAINING > CTL-TIER1-MAX-DAYS
              MOVE CTL-TIER1-MAX-DAYS TO WS-TIER1-DAYS
              SUBTRACT CTL-TIER1-MAX-DAYS FROM WS-REMAINING
           ELSE
              MOVE WS-REMAINING TO WS-TIER1-DAYS
              MOVE 0 TO WS-REMAINING
           END-IF
      *    Tier 2
           IF WS-REMAINING > 0
              IF WS-REMAINING > WS-T2-SPAN
                 MOVE WS-T2-SPAN TO WS-TIER2-DAYS
                 SUBTRACT WS-T2-SPAN FROM WS-REMAINING
              ELSE
                 MOVE WS-REMAINING TO WS-TIER2-DAYS
                 MOVE 0 TO WS-REMAINING
              END-IF
           END-IF
      *    Tier 3
           IF WS-REMAINING > 0
              MOVE WS-REMAINING TO WS-TIER3-DAYS
           END-IF.

       COMPUTE-RAW-FEE.
           COMPUTE WS-TIER1-FEE =
               LN-PRINCIPAL-OUTSTANDING
               * CTL-TIER1-RATE-BP / 10000
               * WS-TIER1-DAYS
           COMPUTE WS-TIER2-FEE =
               LN-PRINCIPAL-OUTSTANDING
               * CTL-TIER2-RATE-BP / 10000
               * WS-TIER2-DAYS
           COMPUTE WS-TIER3-FEE =
               LN-PRINCIPAL-OUTSTANDING
               * CTL-TIER3-RATE-BP / 10000
               * WS-TIER3-DAYS
           COMPUTE WS-RAW-FEE =
               WS-TIER1-FEE + WS-TIER2-FEE + WS-TIER3-FEE.

       APPLY-CAP.
           COMPUTE WS-CAP-AMT =
               LN-PRINCIPAL-OUTSTANDING * CTL-CAP-PCT / 100
           IF WS-RAW-FEE > WS-CAP-AMT
              MOVE WS-CAP-AMT TO WS-CAPPED
           ELSE
              MOVE WS-RAW-FEE TO WS-CAPPED
           END-IF.

       ROUND-HALF-UP.
      *    Half-up to cents. COMPUTE ROUNDED is left as the portable
      *    form; fixtures lock the half-up semantics via the oracle.
           COMPUTE WS-ROUNDED ROUNDED = WS-CAPPED.

       APPLY-MINIMUM.
           IF WS-ROUNDED > 0 AND WS-ROUNDED < CTL-MIN-FEE
              MOVE CTL-MIN-FEE TO WS-ROUNDED
           END-IF.

       END PROGRAM LNFEECALC.
