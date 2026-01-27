import de.quati.kotlin.util.toOption
import de.quati.ogen.UserId
import de.quati.ogen.oas.schemas.gen.model.AllOfMixed1Dto
import de.quati.ogen.oas.schemas.gen.model.AllOfMixed2Dto
import de.quati.ogen.oas.schemas.gen.model.AllOfRef1Dto
import de.quati.ogen.oas.schemas.gen.model.AllOfRef2Dto
import de.quati.ogen.oas.schemas.gen.model.AllOfRefDto
import de.quati.ogen.oas.schemas.gen.model.AnyOfMixed1Dto
import de.quati.ogen.oas.schemas.gen.model.AnyOfMixed2Dto
import de.quati.ogen.oas.schemas.gen.model.AnyOfPrimitiveDto
import de.quati.ogen.oas.schemas.gen.model.AnyOfRefDto
import de.quati.ogen.oas.schemas.gen.model.ArrayOfUnionsDto
import de.quati.ogen.oas.schemas.gen.model.AdditionalPropertiesAnyObjectDto
import de.quati.ogen.oas.schemas.gen.model.AdditionalPropertiesObjectDto
import de.quati.ogen.oas.schemas.gen.model.LocaleDto
import de.quati.ogen.oas.schemas.gen.model.MapInnerObjectDto
import de.quati.ogen.oas.schemas.gen.model.MapPrimitiveDto
import de.quati.ogen.oas.schemas.gen.model.MapRefDto
import de.quati.ogen.oas.schemas.gen.model.NestedUnionDto
import de.quati.ogen.oas.schemas.gen.model.OneOfDiscriminator1Dto
import de.quati.ogen.oas.schemas.gen.model.OneOfDiscriminator2Dto
import de.quati.ogen.oas.schemas.gen.model.OneOfDiscriminatorDto
import de.quati.ogen.oas.schemas.gen.model.OneOfMapping1Dto
import de.quati.ogen.oas.schemas.gen.model.OneOfMapping2Dto
import de.quati.ogen.oas.schemas.gen.model.OneOfMappingDto
import de.quati.ogen.oas.schemas.gen.model.OneOfMixedDto
import de.quati.ogen.oas.schemas.gen.model.OneOfPrimitiveDto
import de.quati.ogen.oas.schemas.gen.model.RecursiveObjectDto
import de.quati.ogen.oas.schemas.gen.model.RoleDto
import de.quati.ogen.oas.schemas.gen.model.TenantDto
import de.quati.ogen.oas.schemas.gen.model.TenantIdDto
import de.quati.ogen.oas.schemas.gen.model.UserDto
import de.quati.ogen.oas.schemas.gen.model.UserNameDto
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.uuid.Uuid

class ModelsV30Test {
    companion object {
        private val json = Json {
            encodeDefaults = false      // omit Undefined (defaults)
            explicitNulls = true        // keep "field": null when Some(null)
        }

        private inline fun <reified T> T.test(expectedString: String? = null) {
            val str = json.encodeToString<T>(this)
            val obj = json.decodeFromString<T>(str)
            obj shouldBe this
            if (expectedString != null)
                str shouldBe expectedString
        }
    }

    @Test
    fun `test UserDto`() {
        UserDto(
            id = UserId("a501c1ca-c20d-4dac-90d4-57905ead8e2f"),
            name = null,
            email = "",
            height = 1.80.toBigDecimal(),
            weight = null,
            verified = true,
            locale = LocaleDto.EN,
            friends = listOf()
        ).test("""{"id":"a501c1ca-c20d-4dac-90d4-57905ead8e2f","name":null,"email":"","height":"1.8","weight":null,"verified":true,"locale":"en","friends":[]}""")
        UserDto(
            id = UserId("c826e9b3-77b6-4f09-b75f-9ab51b4a90d2"),
            name = UserNameDto("FooBar"),
            email = "foobar@example.com",
            height = 1.73.toBigDecimal(),
            weight = 3.14.toBigDecimal(),
            verified = true,
            locale = LocaleDto.DE,
            friends = listOf(
                UserId("16cbd7b5-955f-4757-bf65-dde3e640e8b7"),
                UserId("652f479c-f823-467d-a56c-c6674afcf37d"),
            )
        ).test("""{"id":"c826e9b3-77b6-4f09-b75f-9ab51b4a90d2","name":"FooBar","email":"foobar@example.com","height":"1.73","weight":"3.14","verified":true,"locale":"de","friends":["16cbd7b5-955f-4757-bf65-dde3e640e8b7","652f479c-f823-467d-a56c-c6674afcf37d"]}""")
    }

    @Test
    fun `test enums`() {
        LocaleDto.EN.test(expectedString = "\"en\"")
        LocaleDto.DE.test(expectedString = "\"de\"")

        RoleDto.MEMBER.test(expectedString = "\"member\"")
        RoleDto.ADMIN.test(expectedString = "\"admin\"")
    }

    @Test
    fun `test UserDto nullable fields`() {
        UserDto(
            id = UserId("c826e9b3-77b6-4f09-b75f-9ab51b4a90d2"),
            name = null, // nullable property
            email = "foobar@example.com",
            height = java.math.BigDecimal("1.73"),
            weight = null, // nullable property
            verified = true,
            locale = LocaleDto.DE,
            friends = listOf()
        ).test("""{"id":"c826e9b3-77b6-4f09-b75f-9ab51b4a90d2","name":null,"email":"foobar@example.com","height":"1.73","weight":null,"verified":true,"locale":"de","friends":[]}""")
    }

    @Test
    fun `test Tenant with inner objects members`() {
        TenantDto(
            id = TenantIdDto(Uuid.parse("af09a05a-aeb8-4d9a-b57b-2e1d578aedff")),
            createdAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(123_456_789),
                ZoneId.of("Europe/Berlin")
            ),
            parentId = null,
            name = null,
            members = listOf()
        ).test("""{"id":"af09a05a-aeb8-4d9a-b57b-2e1d578aedff","createdAt":"1973-11-29T22:33:09+01:00","parentId":null,"name":null,"members":[]}""")
        TenantDto(
            id = TenantIdDto(Uuid.parse("0aa3b372-3ae5-4e5c-92c0-9217be60d4cc")),
            createdAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(123_456_789 * 47),
                ZoneId.of("America/New_York")
            ),
            parentId = null,
            name = null,
            members = listOf(
                TenantDto.MembersItemDto(
                    id = UserId("0aa3b372-3ae5-4e5c-92c0-9217be60d4cc"),
                    name = UserNameDto("FooBar"),
                    email = "foo@bar.com",
                    roles = listOf(RoleDto.ADMIN, RoleDto.MEMBER)
                ),
                TenantDto.MembersItemDto(
                    id = UserId("59ba3ff4-b2f7-456d-b80d-5149087c62f1"),
                    name = null,
                    email = "hello@world.com",
                    roles = listOf()
                ),
            )
        ).test("""{"id":"0aa3b372-3ae5-4e5c-92c0-9217be60d4cc","createdAt":"2017-10-08T18:29:47-04:00","parentId":null,"name":null,"members":[{"id":"0aa3b372-3ae5-4e5c-92c0-9217be60d4cc","name":"FooBar","email":"foo@bar.com","roles":["admin","member"]},{"id":"59ba3ff4-b2f7-456d-b80d-5149087c62f1","name":null,"email":"hello@world.com","roles":[]}]}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test oneOf with discriminator`() {
        OneOfDiscriminator1Dto().test(expectedString = """{}""") // TODO ok?
        OneOfDiscriminator2Dto().test(expectedString = """{}""") // TODO ok?
        (OneOfDiscriminator1Dto() as OneOfDiscriminatorDto).test(expectedString = """{"objectType":"OneOfDiscriminator1"}""")
        (OneOfDiscriminator2Dto() as OneOfDiscriminatorDto).test(expectedString = """{"objectType":"OneOfDiscriminator2"}""")

        OneOfDiscriminator1Dto(bar = "hello".toOption()).test(expectedString = """{"bar":"hello"}""") // TODO ok?
        OneOfDiscriminator2Dto(foo = "world".toOption()).test(expectedString = """{"foo":"world"}""") // TODO ok?
        (OneOfDiscriminator1Dto(bar = "abc".toOption()) as OneOfDiscriminatorDto).test(expectedString = """{"objectType":"OneOfDiscriminator1","bar":"abc"}""")
        (OneOfDiscriminator2Dto(foo = "def".toOption()) as OneOfDiscriminatorDto).test(expectedString = """{"objectType":"OneOfDiscriminator2","foo":"def"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test oneOf with discriminator and mapping`() {
        OneOfMapping1Dto().test(expectedString = """{}""") // TODO ok?
        OneOfMapping2Dto().test(expectedString = """{}""") // TODO ok?
        (OneOfMapping1Dto() as OneOfMappingDto).test(expectedString = """{"objectType":"obj1"}""")
        (OneOfMapping2Dto() as OneOfMappingDto).test(expectedString = """{"objectType":"obj2"}""")

        OneOfMapping1Dto(bar = "hello".toOption()).test(expectedString = """{"bar":"hello"}""") // TODO ok?
        OneOfMapping2Dto(foo = "world".toOption()).test(expectedString = """{"foo":"world"}""") // TODO ok?
        (OneOfMapping1Dto(bar = "abc".toOption()) as OneOfMappingDto).test(expectedString = """{"objectType":"obj1","bar":"abc"}""")
        (OneOfMapping2Dto(foo = "def".toOption()) as OneOfMappingDto).test(expectedString = """{"objectType":"obj2","foo":"def"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test allOf with ref`() {
        AllOfRef1Dto(
            bar1 = "abc", bar2 = null,
        ).test(expectedString = """{"bar1":"abc","bar2":null}""")
        AllOfRef1Dto(
            bar1 = "abc", bar2 = "def",
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh"}""")

        AllOfRef2Dto(
            foo1 = "ghi", foo2 = null,
        ).test(expectedString = """{"foo1":"ghi","foo2":null}""")
        AllOfRef2Dto(
            foo1 = "ghi", foo2 = "klm",
            foo3 = "nop".toOption(), foo4 = null.toOption(),
        ).test(expectedString = """{"foo1":"ghi","foo2":"klm","foo3":"nop","foo4":null}""")

        AllOfRefDto(
            bar1 = "abc", bar2 = null,
            foo1 = "ghi", foo2 = null,
        ).test(expectedString = """{"bar1":"abc","bar2":null,"foo1":"ghi","foo2":null}""")
        AllOfRefDto(
            bar1 = "abc", bar2 = "def",
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            foo1 = "ghi", foo2 = "klm",
            foo3 = "nop".toOption(), foo4 = null.toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","foo1":"ghi","foo2":"klm","foo3":"nop","foo4":null}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test allOf with mixed 1`() {
        AllOfMixed1Dto(
            bar1 = "abc", bar2 = null,
            quati1 = "foo"
        ).test(expectedString = """{"bar1":"abc","bar2":null,"quati1":"foo"}""")
        AllOfMixed1Dto(
            bar1 = "abc", bar2 = "def",
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            quati1 = "ghi", quati2 = "klm".toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","quati1":"ghi","quati2":"klm"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test allOf with mixed 2`() {
        AllOfMixed2Dto(
            bar1 = "abc", bar2 = null,
            foo1 = "ghi", foo2 = null,
            quati1 = "foo"
        ).test(expectedString = """{"bar1":"abc","bar2":null,"foo1":"ghi","foo2":null,"quati1":"foo"}""")
        AllOfMixed2Dto(
            bar1 = "abc", bar2 = "def",
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            foo1 = "ghi", foo2 = "klm",
            foo3 = "nop".toOption(), foo4 = null.toOption(),
            quati1 = "ghi", quati2 = "klm".toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","foo1":"ghi","foo2":"klm","foo3":"nop","foo4":null,"quati1":"ghi","quati2":"klm"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test anyOf with ref`() {
        AnyOfRefDto().test(expectedString = """{}""") // TODO actually not valid
        AnyOfRefDto(
            bar1 = "abc".toOption(), bar2 = "def".toOption(),
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            foo1 = "ghi".toOption(), foo2 = "klm".toOption(),
            foo3 = "nop".toOption(), foo4 = null.toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","foo1":"ghi","foo2":"klm","foo3":"nop","foo4":null}""")

        // Edge cases for anyOf: only one side
        AnyOfRefDto(
            bar1 = "abc".toOption(),
            bar2 = null.toOption()
        ).test(expectedString = """{"bar1":"abc","bar2":null}""")

        AnyOfRefDto(
            foo1 = "ghi".toOption(),
            foo2 = "klm".toOption()
        ).test(expectedString = """{"foo1":"ghi","foo2":"klm"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test anyOf with mixed 1`() {
        AnyOfMixed1Dto().test(expectedString = """{}""") // TODO actually not valid
        AnyOfMixed1Dto(
            bar1 = "abc".toOption(), bar2 = "def".toOption(),
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            quati1 = "ghi".toOption(), quati2 = "klm".toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","quati1":"ghi","quati2":"klm"}""")

        // Edge cases for anyOf mixed
        AnyOfMixed1Dto(
            quati1 = "only mixed".toOption()
        ).test(expectedString = """{"quati1":"only mixed"}""")
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `test anyOf with mixed 2`() {
        AnyOfMixed2Dto().test(expectedString = """{}""") // TODO actually not valid
        AnyOfMixed2Dto(
            bar1 = "abc".toOption(), bar2 = "def".toOption(),
            bar3 = "asd".toOption(), bar4 = "fgh".toOption(),
            foo1 = "ghi".toOption(), foo2 = "klm".toOption(),
            foo3 = "nop".toOption(), foo4 = null.toOption(),
            quati1 = "ghi".toOption(), quati2 = "klm".toOption(),
        ).test(expectedString = """{"bar1":"abc","bar2":"def","bar3":"asd","bar4":"fgh","foo1":"ghi","foo2":"klm","foo3":"nop","foo4":null,"quati1":"ghi","quati2":"klm"}""")

        AnyOfMixed2Dto(
            bar1 = "val".toOption(),
            foo1 = "val".toOption(),
            quati1 = "val".toOption()
        ).test(expectedString = """{"bar1":"val","foo1":"val","quati1":"val"}""")
    }

    @Test
    fun `test oneOf primitive`() {
        OneOfPrimitiveDto(JsonPrimitive("hello")).test(expectedString = "\"hello\"")
        OneOfPrimitiveDto(JsonPrimitive(123)).test(expectedString = "123")
        OneOfPrimitiveDto(JsonPrimitive(true)).test(expectedString = "true")
    }

    @Test
    fun `test oneOf mixed`() {
        OneOfMixedDto(JsonPrimitive("hello")).test(expectedString = "\"hello\"")
        OneOfMixedDto(JsonPrimitive(123)).test(expectedString = "123")
        OneOfMixedDto(buildJsonObject {
            put("bar1", "abc")
            put("bar2", null as String?)
        }).test(expectedString = """{"bar1":"abc","bar2":null}""")
    }

    @Test
    fun `test anyOf primitive`() {
        AnyOfPrimitiveDto(JsonPrimitive("hello")).test(expectedString = "\"hello\"")
        AnyOfPrimitiveDto(JsonPrimitive(123)).test(expectedString = "123")
    }

    @Test
    fun `test nested union`() {
        NestedUnionDto(JsonPrimitive("hello")).test(expectedString = "\"hello\"")
        NestedUnionDto(JsonPrimitive(123)).test(expectedString = "123")
        NestedUnionDto(JsonPrimitive(false)).test(expectedString = "false")
    }

    @Test
    fun `test additional properties`() {
        AdditionalPropertiesObjectDto(buildJsonObject {
            put("knownProperty", "value")
            put("extra1", 1)
            put("extra2", 2)
        }).test(expectedString = """{"knownProperty":"value","extra1":1,"extra2":2}""")

        AdditionalPropertiesAnyObjectDto(buildJsonObject {
            put("foo", "bar")
            put("baz", true)
            put("num", 42)
        }).test(expectedString = """{"foo":"bar","baz":true,"num":42}""")
    }

    @Test
    fun `test array of unions`() {
        ArrayOfUnionsDto(
            listOf(
                OneOfPrimitiveDto(JsonPrimitive("a")),
                OneOfPrimitiveDto(JsonPrimitive(1)),
                OneOfPrimitiveDto(JsonPrimitive(true))
            )
        ).test(expectedString = """["a",1,true]""")
    }

    @Test
    fun `test recursive object`() {
        RecursiveObjectDto(
            name = "child".toOption(),
            parent = RecursiveObjectDto(
                name = "parent".toOption()
            ).toOption()
        ).test(expectedString = """{"name":"child","parent":{"name":"parent"}}""")

        RecursiveObjectDto(
            name = "root".toOption(),
            parent = null.toOption()
        ).test(expectedString = """{"name":"root","parent":null}""")

        RecursiveObjectDto(
            name = "root".toOption(),
        ).test(expectedString = """{"name":"root"}""")
    }

    @Test
    fun `test map object with ref`() {
        MapRefDto(
            mapOf(
                "key1" to OneOfPrimitiveDto(JsonPrimitive("val1")),
                "key2" to OneOfPrimitiveDto(JsonPrimitive(42)),
                "key3" to OneOfPrimitiveDto(JsonPrimitive(true))
            )
        ).test(expectedString = """{"key1":"val1","key2":42,"key3":true}""")
    }

    @Test
    fun `test map object with primitive`() {
        MapPrimitiveDto(mapOf(
            "key1" to 47,
            "key2" to 3,
            "key3" to 0
        )).test(expectedString = """{"key1":47,"key2":3,"key3":0}""")
    }

    @Test
    fun `test map object with inner object`() {
        MapInnerObjectDto(mapOf(
            "key1" to MapInnerObjectDto.ValueDto(),
            "key2" to MapInnerObjectDto.ValueDto(
                foo = "gg".toOption(),
            ),
            "key3" to MapInnerObjectDto.ValueDto(
                foo = "xy".toOption(),
                bar = 42.toOption()
            ),
        )).test(expectedString = """{"key1":{},"key2":{"foo":"gg"},"key3":{"foo":"xy","bar":42}}""")
    }
}