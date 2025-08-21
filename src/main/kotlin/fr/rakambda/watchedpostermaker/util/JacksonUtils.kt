package fr.rakambda.watchedpostermaker.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.ZoneId.systemDefault
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

object JacksonUtils {
    object ISO8601ZonedDateTimeDeserializer : JsonDeserializer<ZonedDateTime>() {
        override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): ZonedDateTime {
            return ZonedDateTime.parse(jsonParser.valueAsString, ISO_DATE_TIME).withZoneSameInstant(systemDefault());
        }
    }
}