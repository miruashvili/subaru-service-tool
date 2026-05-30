package com.subaru.servicetool.data.obd.discovery

/** Result of a module discovery attempt. */
enum class ModuleStatus {
    /** Module has not been probed yet. */
    UNKNOWN,
    /** Module responded to at least one probe command. */
    PRESENT,
    /** Module did not respond to any probe command within the timeout. */
    ABSENT,
    /** Header switch (ATSH) timed out — discovery could not proceed for this module. */
    ERROR,
}
