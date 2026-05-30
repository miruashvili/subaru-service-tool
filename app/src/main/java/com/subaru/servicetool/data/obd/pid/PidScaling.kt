package com.subaru.servicetool.data.obd.pid

/**
 * Documents the scaling formula applied to a PID's raw bytes to produce an engineering value.
 *
 * This is metadata for documentation, tooling, and future auto-generation of decoders.
 * The actual computation is performed by [PidDecoder]. Both should be consistent — the
 * [SubaruPidDefinitions] uses factory helpers from [PidDecoder.Companion] that derive both
 * the scaling descriptor and the corresponding decode lambda from the same parameters.
 */
sealed class PidScaling {

    /** Single byte: value = A × [factor] + [offset]. */
    data class Linear(val factor: Float, val offset: Float = 0f) : PidScaling()

    /** Two bytes big-endian: value = (A×256 + B) × [factor] + [offset]. */
    data class TwoByteLinear(val factor: Float, val offset: Float = 0f) : PidScaling()

    /** Two bytes big-endian signed: (A−128) × [factor] + [offset]. */
    data class SignedTwoByteLinear(val factor: Float, val offset: Float = 0f) : PidScaling()

    /** Single byte, no transformation: value = A. */
    object Raw : PidScaling()

    /** Percentage: value = A / 255 × 100 %. */
    object Percentage : PidScaling()

    /** Temperature offset: value = A − 40 °C. */
    object Temperature : PidScaling()

    /** Engine speed: value = (A×256 + B) / 4 rpm. */
    object Rpm : PidScaling()

    /** OBD-II fuel trim: value = (A − 128) × 100 / 128 %. */
    object FuelTrim : PidScaling()

    /** Signed centred on 128: value = (A − 128) × 0.5 (VVT advance, knock correction). */
    object SignedCentered : PidScaling()

    /** OBD-II AFR from equivalence ratio (Mode 01 PID 0x24): value = 2×(A×256+B)/65536×14.7. */
    object Afr : PidScaling()

    /** EGT: value = (A×256 + B) × 0.1 − 40 °C. */
    object Egt : PidScaling()

    /** Throttle motor: value = A × 100 / 255 − 50 %. */
    object ThrottleMotor : PidScaling()

    /**
     * Custom formula — the actual computation is in [PidDecoder] and not expressible
     * as one of the standard forms above.
     */
    object Custom : PidScaling()
}
