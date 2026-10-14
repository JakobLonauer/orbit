package io.github.thelony97.orbit.core.domain.migration

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.core.model.Relation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Schema 2 (as 1.0.101 and the R2/R3 candidates write it) -> 3: Org-Beziehungen werden Zuordnungen. */
class MigrateV2Test {
    private val v2 = """
        {"schema":2,"contacts":[
          {"id":"me","isMe":true,"firstName":"Ich"},
          {"id":"anna","firstName":"Anna"},
          {"id":"tom","firstName":"Tom"},
          {"id":"arzt","kind":"ORG","company":"Arzt Müller"},
          {"id":"jku","kind":"ORG","company":"JKU"},
          {"id":"bank","kind":"ORG","company":"Bank"}
        ],
        "relations":[
          {"id":"r1","fromId":"anna","toId":"tom","kind":"partner"},
          {"id":"r2","fromId":"anna","toId":"arzt","kind":"patient"},
          {"id":"r3","fromId":"jku","toId":"me","kind":"works_at"},
          {"id":"r4","fromId":"tom","toId":"bank","kind":"Sparbuch"},
          {"id":"r5","fromId":"jku","toId":"bank","kind":"client"},
          {"id":"r6","fromId":"anna","toId":"weg","kind":"works_at"},
          {"id":"r7","fromId":"tom","toId":"jku","kind":"friend"}
        ],
        "orgLinks":[{"id":"old","personId":"anna","orgId":"jku","role":"school"}],
        "settings":{"onboardingDone":true,"digestHour":8}}
    """.trimIndent()

    private val up = MigrateV2.upgrade(DocJson.decode(v2))

    @Test fun personOrgRelationsBecomeZuordnungenFromEitherSide() {
        assertEquals(3, up.schema)
        assertEquals(
            listOf(
                OrgLink("old", "anna", "jku", "school"),
                OrgLink("r2", "anna", "arzt", "doctor"),
                OrgLink("r3", "me", "jku", "employer"),
                OrgLink("r4", "tom", "bank", "custom", "Sparbuch"),
                OrgLink("r7", "tom", "jku", "custom", "Freund/in"),
            ),
            up.orgLinks,
        )
    }

    @Test fun everythingElseStaysARelation() {
        // People among themselves, two organisations, and a relation whose other side is gone.
        assertEquals(
            listOf(
                Relation("r1", "anna", "tom", "partner"),
                Relation("r5", "jku", "bank", "client"),
                Relation("r6", "anna", "weg", "works_at"),
            ),
            up.relations,
        )
        assertEquals(DocJson.decode(v2).contacts, up.contacts)
        assertEquals(8, up.settings.digestHour)
    }

    @Test fun idempotentAndRoundTrips() {
        assertEquals(up, MigrateV2.upgrade(up))
        val back = DocJson.decode(DocJson.encode(up))
        assertEquals(up, back)
        assertEquals(3, MigrateV1.schemaOf(DocJson.encode(up)))
    }

    @Test fun sameZuordnungTwiceIsKeptOnce() {
        val doc = DocJson.decode(v2).let { it.copy(relations = it.relations + Relation("r8", "arzt", "anna", "patient")) }
        assertEquals(1, MigrateV2.upgrade(doc).orgLinks.count { it.personId == "anna" && it.orgId == "arzt" })
    }
}
