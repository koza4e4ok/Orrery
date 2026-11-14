package me.kozakov.orrery

/**
 * Marks API that is public but not yet stable: it may change or disappear in any
 * release, including patch releases, without a deprecation cycle.
 *
 * Every public declaration of `orrery-widget` carries this marker for the whole
 * 1.0.x line; the plan is to remove it in 1.1 after an API review. Callers opt in with
 * `@OptIn(ExperimentalOrreryApi::class)` (or the `-opt-in` compiler flag) to
 * acknowledge that. Marked declarations are left out of the published ABI dumps, so
 * the binary-compatibility checks do not cover them.
 */
@RequiresOptIn(
    message = "This Orrery API is experimental and may change in any release without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS,
)
@MustBeDocumented
public annotation class ExperimentalOrreryApi

/**
 * Marks declarations that are public only so that sibling `orrery-*` modules can use
 * them, for example the month-drawing primitives that `orrery-compose` shares with
 * `orrery-widget`.
 *
 * They are not part of Orrery's API: no source or binary compatibility is promised,
 * they are left out of the ABI dumps and the API docs, and they only work when every
 * `orrery-*` artefact has the same version. Using them outside this repository is an
 * error unless the caller opts in explicitly, and doing so is not supported.
 */
@RequiresOptIn(
    message = "This is internal Orrery API for use by orrery-* modules only; it can change in any release.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS,
)
@MustBeDocumented
public annotation class InternalOrreryApi
