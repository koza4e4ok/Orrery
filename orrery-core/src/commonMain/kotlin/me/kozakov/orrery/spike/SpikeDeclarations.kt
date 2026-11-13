package me.kozakov.orrery.spike

import me.kozakov.orrery.spike.markers.SpikeExperimentalApi
import me.kozakov.orrery.spike.markers.SpikeInternalApi

// Spike (#89 task 1, item b): every declaration here must stay out of the JVM and klib
// dumps through the annotatedWith filter alone (this package is not excluded by name).

/** Experimental class. */
@SpikeExperimentalApi
public class SpikeExperimentalClass {
    /** Member of an experimental class. */
    public fun value(): Int = hashCode()
}

/** Experimental interface. */
@SpikeExperimentalApi
public interface SpikeExperimentalInterface

/** Experimental object. */
@SpikeExperimentalApi
public object SpikeExperimentalObject

/** Internal top-level function. */
@SpikeInternalApi
public fun spikeInternalFunction(): Int = "spike".length

/** Experimental top-level property. */
@SpikeExperimentalApi
public val spikeExperimentalProperty: Int = "spike".length

/** Experimental type alias. */
@SpikeExperimentalApi
public typealias SpikeExperimentalAlias = SpikeExperimentalClass
