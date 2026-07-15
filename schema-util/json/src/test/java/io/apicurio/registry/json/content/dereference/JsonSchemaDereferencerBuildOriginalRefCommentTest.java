package io.apicurio.registry.json.content.dereference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class JsonSchemaDereferencerBuildOriginalRefCommentTest {

    @Test
    void buildOriginalRefComment() {
        Assertions.assertEquals(Optional.of("customer.json:orders/Customer:1"),
                JsonSchemaDereferencer.buildOriginalRefComment("orders:Customer:1:customer.json"));
        Assertions.assertEquals(Optional.of("types/all-types.json:default/City:2"),
                JsonSchemaDereferencer
                        .buildOriginalRefComment("default:City:2:types/all-types.json#/definitions/City"));
        Assertions.assertTrue(JsonSchemaDereferencer.buildOriginalRefComment("customer.json").isEmpty());
        Assertions.assertTrue(JsonSchemaDereferencer.buildOriginalRefComment(null).isEmpty());
    }
}
