      *----------------------------------------------------------------*
      * LNFEEREC - Loan late-fee work area                             *
      * Used by LNFEECALC. Host programs populate INPUT fields, then   *
      * call LNFEECALC, which fills OUTPUT fields.                     *
      *----------------------------------------------------------------*
       01  LN-FEE-RECORD.
           05  LN-FEE-INPUT.
               10  LN-PRINCIPAL-OUTSTANDING PIC 9(13)V99.
               10  LN-DAYS-OVERDUE          PIC 9(05).
               10  LN-PRODUCT-CODE          PIC X(08).
               10  LN-CURRENCY-CODE         PIC X(03).
           05  LN-FEE-OUTPUT.
               10  LN-LATE-FEE-AMT          PIC 9(13)V99.
               10  LN-BILLABLE-DAYS         PIC 9(05).
               10  LN-STATUS-CODE           PIC X(02).
               10  LN-STATUS-MSG            PIC X(40).
