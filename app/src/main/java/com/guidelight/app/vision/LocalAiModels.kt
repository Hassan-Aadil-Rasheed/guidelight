package com.guidelight.app.vision

data class LocalModelFile(val name: String,val bytes: Long,val hash: String)
enum class LocalAiProfile(val label: String,val repository: String,val revision: String,val thinking: Boolean,val files: List<LocalModelFile>) {
    FAST("Fast vision","LiquidAI/LFM2.5-VL-450M-GGUF","1abed04b6fe71314d8c446a1371c03d7c332266d",false,listOf(
        LocalModelFile("LFM2.5-VL-450M-Q4_0.gguf",219311264,"6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf"),
        LocalModelFile("mmproj-LFM2.5-VL-450m-Q8_0.gguf",102815168,"ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561"))),
    DETAILED("Detailed reasoning","LiquidAI/LFM3-VL-2B-GGUF","017b19da2dcffadb41d67745941f3d785f0d162f",true,listOf(
        LocalModelFile("LFM3-VL-2B-QAD-Q4_0.gguf",1049615648,"621c455376cb96a20891697deb9975c6fb47dd5ea98ed3ad1f3b7e8f24f0b1e6"),
        LocalModelFile("mmproj-LFM3-VL-2B-Q8_0.gguf",203031872,"c5c5b58d544a18da919172d2d0368090ea1a4a351b6feb7f8a326a264a5918ab")))
}
