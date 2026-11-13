package me.kozakov.orrery.spike.markers

// Spike (#89 task 1, item b): stand-ins for ExperimentalOrreryApi / InternalOrreryApi.
// This package is excluded from the ABI dumps by name (the package fallback of spec 1.4).

/** Experimental marker used by the ABI filter spike. */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class SpikeExperimentalApi

/** Internal marker used by the ABI filter spike. */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class SpikeInternalApi
