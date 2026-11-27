package com.example.bluetoothtracker.domain.logic

/**
 * Small starter list. Later: bundle the full Bluetooth SIG company-identifier list
 * as a JSON asset and look it up here.
 */
object CompanyResolver {
    private val companies = mapOf(
        0x0006 to "Microsoft",
        0x004C to "Apple, Inc.",
        0x0075 to "Samsung Electronics",
        0x009E to "Bose Corporation",
        0x00E0 to "Google",
        0x012D to "Sony Corporation",
        0x027D to "Huawei Technologies",
        0x038F to "Xiaomi"
    )

    fun nameFor(companyId: Int?): String? = companyId?.let { companies[it] }
}
