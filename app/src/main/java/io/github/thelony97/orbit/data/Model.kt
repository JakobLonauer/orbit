package io.github.thelony97.orbit.data

/*
 * Storage format v2 lives in :core:model (ADR 0001/0003). These aliases keep the app's
 * historical names so screens did not need a rename; the frozen 1.0.7 format is
 * io.github.thelony97.orbit.core.model.v1 and only the migration reads it.
 */

typealias OrbitData = io.github.thelony97.orbit.core.model.OrbitDoc
typealias Contact = io.github.thelony97.orbit.core.model.Contact
typealias ContactKind = io.github.thelony97.orbit.core.model.ContactKind
typealias LabeledValue = io.github.thelony97.orbit.core.model.LabeledValue
typealias DayOfYear = io.github.thelony97.orbit.core.model.CalendarDay
typealias Anniversary = io.github.thelony97.orbit.core.model.Occasion
typealias CheckItem = io.github.thelony97.orbit.core.model.CheckItem
typealias SmsMessage = io.github.thelony97.orbit.core.model.SmsMessage
typealias EntrySource = io.github.thelony97.orbit.core.model.EntrySource
typealias Entry = io.github.thelony97.orbit.core.model.HistoryEntry
typealias GeoPoint = io.github.thelony97.orbit.core.model.GeoPoint
typealias GalleryPhoto = io.github.thelony97.orbit.core.model.Attachment
typealias Group = io.github.thelony97.orbit.core.model.Group
typealias EntryType = io.github.thelony97.orbit.core.model.EntryType
typealias Relation = io.github.thelony97.orbit.core.model.Relation
typealias OrgLink = io.github.thelony97.orbit.core.model.OrgLink
typealias Meeting = io.github.thelony97.orbit.core.model.Meeting
typealias UnknownNumber = io.github.thelony97.orbit.core.model.UnknownNumber
typealias Settings = io.github.thelony97.orbit.core.model.Settings
