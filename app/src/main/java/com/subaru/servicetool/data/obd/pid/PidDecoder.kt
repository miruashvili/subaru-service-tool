package com.subaru.servicetool.data.obd.pid

/**
 * Converts a list of raw bytes extracted by a [parser.ResponseParser] into a
 * floating-point engineering value.
 *
 * Implementors are pure functions — they carry no state and produce a deterministic
 * output for any given input. Return null when the byte list is too short or the
 * value falls outside the sensor's valid physical range.
 *
 * Pre-built decoders for all standard Subaru scaling formulas are available via the
 * [Companion] factory. Custom decoders can be created as lambdas:
 *
 * ```kotlin
 * val myDecoder = PidDecoder { bytes -> bytes.firstOrNull()?.toFloat()?.times(0.5f) }
 * ```
 */
fun interface PidDecoder {

    /** Decodes [bytes] to a physical value, or null if the bytes are invalid. */
    fun decode(bytes: List<Int>): Float?

    companion object {

        // ── Temperature ──────────────────────────────────────────────────────

        /** A − 40 °C. Clamps to [-40, 215] and returns null outside range. */
        val temperature: PidDecoder = PidDecoder { b ->
            if (b.isEmpty()) null
            else { val v = b[0] - 40; if (v < -40 || v > 215) null else v.toFloat() }
        }

        /** A − 40 °C, CVT fluid variant — valid range [-40, 150]. */
        val cvtTemperature: PidDecoder = PidDecoder { b ->
            if (b.isEmpty()) null
            else { val v = b[0] - 40; if (v < -40 || v > 150) null else v.toFloat() }
        }

        // ── Speed and RPM ─────────────────────────────────────────────────────

        /** Engine RPM: (A×256 + B) / 4. */
        val rpm: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 4f else null
        }

        /** Vehicle speed: A km/h. */
        val speed: PidDecoder = PidDecoder { b -> b.firstOrNull()?.toFloat() }

        /** Two-byte revolution counter: (A×256 + B) rpm (pulley / turbine). */
        val twoByteRpm: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null
        }

        // ── Pressure ─────────────────────────────────────────────────────────

        /** Manifold / barometric pressure: A kPa. */
        val pressure: PidDecoder = PidDecoder { b -> b.firstOrNull()?.toFloat() }

        /** TPMS pressure: (A×256 + B) / 10 kPa. */
        val tpmsPressure: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 10f else null
        }

        // ── Percentage ───────────────────────────────────────────────────────

        /** A / 255 × 100 %. */
        val percentage: PidDecoder = PidDecoder { b ->
            b.firstOrNull()?.let { it / 255f * 100f }
        }

        /** Throttle position: A / 2.55 %. Equivalent to A / 255 × 100. */
        val throttle: PidDecoder = percentage

        /** Absolute engine load: (A×256 + B) / 2.55 %. */
        val absoluteLoad: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 2.55f else null
        }

        // ── Voltage ───────────────────────────────────────────────────────────

        /** O2 sensor: A / 200 V. */
        val o2Voltage: PidDecoder = PidDecoder { b ->
            b.firstOrNull()?.let { it / 200f }
        }

        /** Battery voltage (Mode 01 PID 0x42): (A×256 + B) / 1000 V. */
        val batteryVoltage: PidDecoder = twoByteLinear(1f / 1000f)

        /** MAF sensor output voltage: A × 0.02 V. */
        val mafVoltage: PidDecoder = linear(0.02f)

        // ── Flow ─────────────────────────────────────────────────────────────

        /** Mass air flow: (A×256 + B) / 100 g/s. */
        val maf: PidDecoder = twoByteLinear(1f / 100f)

        // ── Fuel ─────────────────────────────────────────────────────────────

        /** OBD-II fuel trim: (A − 128) × 100 / 128 %. */
        val fuelTrim: PidDecoder = PidDecoder { b ->
            b.firstOrNull()?.let { (it - 128) * 100f / 128f }
        }

        /** Fuel level: A / 255 × 100 %. */
        val fuelLevel: PidDecoder = percentage

        /** AFR from equivalence ratio (Mode 01 PID 0x24): 2 × (A×256+B) / 65536 × 14.7. */
        val afr: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) 2f * (b[0] * 256 + b[1]).toFloat() / 65536f * 14.7f else null
        }

        // ── Signed / centred ─────────────────────────────────────────────────

        /** VVT advance / knock correction: (A − 128) × 0.5 °. */
        val signedCentered: PidDecoder = PidDecoder { b ->
            b.firstOrNull()?.let { (it.toFloat() - 128f) * 0.5f }
        }

        /** AWD / lockup duty: A × 100 / 255 %. */
        val awdDuty: PidDecoder = percentage

        // ── Exhaust ───────────────────────────────────────────────────────────

        /** Exhaust gas temperature: (A×256 + B) × 0.1 − 40 °C. */
        val egt: PidDecoder = PidDecoder { b ->
            if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() * 0.1f - 40f else null
        }

        // ── Throttle motor ────────────────────────────────────────────────────

        /** Electronic throttle motor duty: A × 100 / 255 − 50 %. */
        val throttleMotor: PidDecoder = PidDecoder { b ->
            b.firstOrNull()?.let { it.toFloat() * 100f / 255f - 50f }
        }

        // ── Gear ratio ────────────────────────────────────────────────────────

        /** CVT gear ratio: (A×256 + B) / 1000. */
        val gearRatio: PidDecoder = twoByteLinear(1f / 1000f)

        // ── Time ─────────────────────────────────────────────────────────────

        /** Run time: (A×256 + B) seconds. */
        val runTime: PidDecoder = twoByteLinear(1f)

        // ── Factory helpers ───────────────────────────────────────────────────

        /** Single-byte linear: A × [factor] + [offset]. */
        fun linear(factor: Float, offset: Float = 0f): PidDecoder =
            PidDecoder { b -> b.firstOrNull()?.let { it * factor + offset } }

        /** Two-byte big-endian linear: (A×256 + B) × [factor] + [offset]. */
        fun twoByteLinear(factor: Float, offset: Float = 0f): PidDecoder =
            PidDecoder { b ->
                if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() * factor + offset else null
            }

        /** Byte at [index]: b[[index]] × [factor] + [offset]. */
        fun byteAt(index: Int, factor: Float = 1f, offset: Float = 0f): PidDecoder =
            PidDecoder { b ->
                if (b.size > index) b[index] * factor + offset else null
            }
    }
}
