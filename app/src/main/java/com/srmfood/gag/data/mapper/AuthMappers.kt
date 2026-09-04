package com.srmfood.gag.data.mapper

import com.srmfood.gag.data.local.entity.UserEntity
import com.srmfood.gag.data.remote.dto.UserDto
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.model.UserRole

fun UserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    phone = phone,
    role = UserRole.fromString(role),
    profileImageUrl = profileImageUrl,
    registrationNumber = registrationNumber,
    isActive = isActive,
    createdAt = createdAt
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    phone = phone,
    role = role,
    profileImageUrl = profileImageUrl,
    registrationNumber = registrationNumber,
    isActive = isActive,
    createdAt = createdAt
)

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    phone = phone,
    role = UserRole.fromString(role),
    profileImageUrl = profileImageUrl,
    registrationNumber = registrationNumber,
    isActive = isActive,
    createdAt = createdAt
)
