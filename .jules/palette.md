## 2024-05-20 - Missing Role Semantics on Selectable Elements
**Learning:** In Compose, `Modifier.selectable` doesn't automatically imply a `Role.Button` to screen readers, unlike some native counterparts. While it manages selection state, adding the explicit role helps assistive technologies accurately describe the interaction paradigm.
**Action:** Always check interactive modifiers (`clickable`, `selectable`, `toggleable`) to ensure an appropriate `Role` is assigned if it acts as a primary action target.
