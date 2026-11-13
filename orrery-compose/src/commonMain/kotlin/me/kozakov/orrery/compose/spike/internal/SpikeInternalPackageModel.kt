package me.kozakov.orrery.compose.spike.internal

// Spike (#89 task 1, item b): un-annotated public class in a package excluded by name.
// Checks whether the package filter also drops the Compose compiler's synthetic
// top-level `$stableprop` declarations from the klib dump.

/** Model class hidden by the package filter. */
public class SpikeInternalPackageModel(
    /** Number of rows. */
    public val rows: Int,
)
