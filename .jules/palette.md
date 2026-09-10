## 2024-05-24 - Keyboard UX for Date Input
**Learning:** Using appropriate `KeyboardOptions` (specifically `KeyboardType.Number` and `ImeAction.Next`/`ImeAction.Done`) significantly improves data entry UX on mobile date range pickers.
**Action:** Always verify text inputs have appropriate IME actions, especially when placed in sequence (e.g., Start Date -> End Date).
