      *----------------------------------------------------------------*
      * LNFEECTL - Late-fee product control parameters                 *
      * One occurrence per product. Loaded from the control card at    *
      * batch start; LNFEECALC looks up by LN-PRODUCT-CODE.            *
      * Rates are in basis points of principal per billable day.       *
      *----------------------------------------------------------------*
       01  LN-FEE-CONTROL.
           05  CTL-PRODUCT-CODE             PIC X(08).
           05  CTL-GRACE-DAYS               PIC 9(03).
           05  CTL-TIER1-MAX-DAYS           PIC 9(03).
           05  CTL-TIER2-MAX-DAYS           PIC 9(03).
           05  CTL-TIER1-RATE-BP            PIC 9(04).
           05  CTL-TIER2-RATE-BP            PIC 9(04).
           05  CTL-TIER3-RATE-BP            PIC 9(04).
           05  CTL-CAP-PCT                  PIC 9(03).
           05  CTL-MIN-FEE                  PIC 9(07)V99.
