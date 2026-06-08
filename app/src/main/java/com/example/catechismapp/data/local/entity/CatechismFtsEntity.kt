package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = CatechismEntity::class)
@Entity(tableName = "catechism_fts")
data class CatechismFtsEntity(
    @ColumnInfo(name = "text") val text: String
)
