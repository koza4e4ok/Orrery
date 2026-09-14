## 2024-05-20 - Missing Role Semantics on Selectable Elements
**Learning:** In Compose, `Modifier.selectable` doesn't automatically imply a `Role.Button` to screen readers, unlike some native counterparts. While it manages selection state, adding the explicit role helps assistive technologies accurately describe the interaction paradigm.
**Action:** Always check interactive modifiers (`clickable`, `selectable`, `toggleable`) to ensure an appropriate `Role` is assigned if it acts as a primary action target.
## 2024-05-24 - Keyboard UX for Date Input
**Learning:** Using appropriate `KeyboardOptions` (specifically `KeyboardType.Number` and `ImeAction.Next`/`ImeAction.Done`) significantly improves data entry UX on mobile date range pickers.
**Action:** Always verify text inputs have appropriate IME actions, especially when placed in sequence (e.g., Start Date -> End Date).
## 2024-05-14 - Modifier.clickable Accessibility Semantics
**Learning:** `Modifier.clickable` does not implicitly add a role to elements, which can cause screen readers to read interactive elements incorrectly.
**Action:** Always explicitly pass an appropriate role (e.g., `role = Role.Button`) to `Modifier.clickable` so assistive technologies can accurately interpret the semantic meaning of the element.
