package com.example.codeonly.feature.connection

data class ProviderOption(
    val id: String,
    val name: String,
    val models: List<ModelOption>
)

data class ModelOption(
    val id: String,
    val name: String
)
