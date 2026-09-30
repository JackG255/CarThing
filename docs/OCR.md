# Receipt, invoice and odometer reading

Everything runs on the device with ML Kit text recognition, using the bundled Latin model, which
works offline and includes Czech diacritics. Receipt reading is opt-in: the form offers to read a
photo (`ReadOffer`) and never reads one automatically.

## Pipeline
```
photo ─► ReceiptTextReader.readRows ─► TextRows.group ─► parser ─► prefilled form fields
          (ML Kit lines + boxes)       (rows, tilt-corrected)
```
- **`TextRows.group`** joins recognised lines that sit on the same row, so a label and its amount come back together (`"CELKEM   1 650,17 Kč"`). It corrects for photo tilt using the median line angle.
- **`fold` / `repair`** (in `FuelReceiptParser`) upper-case the text, strip diacritics and fix common recognition mistakes. The rules therefore match `KC`, `MNOZSTVI` and so on.
- **`Numbers`** parses Czech number formats: `1 650,17`, `1.650,17`, `42,15`. A lone `42.150` is read as 42.15, not 42,150.
- **`ReceiptDates`** finds a document date. It skips dates in the future or more than 5 years old, and supports preferred and avoided keywords.

Debug builds log the recognised rows under the `CarThing` tag to help tune the rules. Release builds never log receipt text.

## Parsers
| Parser | Output | Key rules |
|---|---|---|
| `FuelReceiptParser` | `FuelReceipt`: date, litres, price/L, total, station, fuel type | Known station brands and fuel names; keyword rows (`CELKEM`, `MNOZSTVI`, `CENA/L`); ignores tax and ID rows (`ICO`, `DPH`…). Checks litres × price against the total and drops the total if it's off by more than 2%. Fills in a missing value when the other two are known |
| `ServiceInvoiceParser` | `ServiceInvoice`: date, total, shop, odometer, service kinds | Prefers the amount payable over net and VAT lines, and the date of issue or work over the due date (`SPLATNOST`). Reads the odometer from rows like `STAV KM`. Service kinds come from keyword stems (`OLEJ` → Oil change, `PNEU` → Tires…) |
| `OdometerParser` | Up to 3 candidate km values, best first | Skips trip, range, consumption and clock rows. Longer numbers score higher, and a `KM` unit or `ODO` word adds points. When the last known reading is available, values closest to it win and anything more than 100 km below it is dropped. Gauge-scale numbers are dropped once a longer reading is seen |

Parsers return `null` for anything they can't read reliably. It's better to leave a field empty than to fill it with a wrong value.

## Adding cases
The parsers are pure functions over `List<String>` rows, so tests don't need images:
1. Take the rows from the debug log for a real receipt, with personal data removed.
2. Add a test to `FuelReceiptParserTest`, `ServiceInvoiceParserTest` or `OdometerParserTest`, passing a fixed `today` date.
3. Change the rules until the test passes without breaking the existing cases.

## Limits
- Tuned for Czech receipts and invoices (CZK, Czech keywords). Other countries will mostly return nulls.
- Only Latin script is supported.
- Handwritten amounts and heavily faded thermal paper often can't be read.
