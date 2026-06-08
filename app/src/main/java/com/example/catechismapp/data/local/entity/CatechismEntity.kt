package com.example.catechismapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catechism")
data class CatechismEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "text") val text: String
)
