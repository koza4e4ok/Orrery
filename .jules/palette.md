## 2024-05-20 - Role semantics on interactive elements (already done)
**Learning:** `Modifier.clickable` and `Modifier.selectable` do not imply a role, so screen readers cannot tell users an element is a button.
**Action:** Already applied: every interactive element in orrery-compose passes `role = Role.Button` (merged in #4/#6). Do not propose further Role.Button PRs; only set a role on newly added `clickable`/`selectable`/`toggleable` modifiers.

## 2024-05-24 - Keyboard UX for Date Input
**Learning:** Using appropriate `KeyboardOptions` (specifically `KeyboardType.Number` and `ImeAction.Next`/`ImeAction.Done`) significantly improves data entry UX on mobile date range pickers.
**Action:** Always verify text inputs have appropriate IME actions, especially when placed in sequence (e.g., Start Date -> End Date).
