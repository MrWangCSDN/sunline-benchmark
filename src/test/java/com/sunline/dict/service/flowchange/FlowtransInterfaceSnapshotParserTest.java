package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowtransInterfaceSnapshotParserTest {

    private final FlowtransInterfaceSnapshotParser parser = new FlowtransInterfaceSnapshotParser();

    @Test
    void parses_input_output_nested_fields_without_requiring_ref() {
        String xml = """
                <flowtran><interface id="TC045" longname="双公定期手工转存">
                  <input>
                    <field id="top" type="T1" required="true"/>
                    <fields id="accounts"><field id="amount" fixed="2"/></fields>
                  </input>
                  <output><field id="result" ref="MDict.R.result"/></output>
                </interface></flowtran>
                """;

        FlowtransInterfaceSnapshot snapshot = parser.parse(xml);

        assertEquals("TC045", snapshot.flowId());
        assertEquals("双公定期手工转存", snapshot.flowLongname());
        assertTrue(snapshot.fields().containsKey(
                new FieldIdentity("input", "/fields[accounts]", "amount")));
        assertEquals("2", snapshot.fields().get(
                new FieldIdentity("input", "/fields[accounts]", "amount"))
                .attributes().get("fixed"));
        assertTrue(snapshot.fields().containsKey(
                new FieldIdentity("output", "/", "result")));
    }

    @Test
    void captures_every_attribute_case_sensitively() {
        String xml = """
                <flowtran><interface id="TC046" longname="Case">
                  <input><field id="value" Type="upper" type="lower" custom=""/></input>
                </interface></flowtran>
                """;

        FlowtransInterfaceSnapshot snapshot = parser.parse(xml);
        Map<String, String> attributes = snapshot.fields().get(
                new FieldIdentity("input", "/", "value")).attributes();

        assertEquals(Map.of("Type", "upper", "custom", "", "id", "value", "type", "lower"), attributes);
        assertFalse(attributes.containsKey("TYPE"));
    }

    @Test
    void skips_fields_containers_and_handles_empty_input_output() {
        String xml = """
                <flowtran><interface id="TC047">
                  <input><fields id="group" type="container"/></input>
                  <output/>
                </interface></flowtran>
                """;

        FlowtransInterfaceSnapshot snapshot = parser.parse(xml);

        assertTrue(snapshot.fields().isEmpty());
    }

    @Test
    void identifies_anonymous_containers_with_a_stable_sorted_attribute_digest() {
        String firstXml = """
                <flowtran><interface id="TC048"><input>
                  <fields b="2" a="1"><field id="amount"/></fields>
                </input></interface></flowtran>
                """;
        String reorderedAttributesXml = """
                <flowtran><interface id="TC048"><input>
                  <fields a="1" b="2"><field id="amount"/></fields>
                </input></interface></flowtran>
                """;

        FieldIdentity firstIdentity = parser.parse(firstXml).fields().keySet().iterator().next();
        FieldIdentity reorderedIdentity = parser.parse(reorderedAttributesXml).fields().keySet().iterator().next();

        assertEquals(firstIdentity, reorderedIdentity);
        assertTrue(firstIdentity.fieldPath().matches("/fields\\[#[0-9a-f]{12}\\]"));
    }

    @Test
    void keeps_field_identities_stable_when_siblings_are_reordered() {
        String firstXml = """
                <flowtran><interface id="TC049"><input>
                  <fields id="accounts"><field id="amount"/></fields>
                  <fields id="cards"><field id="number"/></fields>
                </input></interface></flowtran>
                """;
        String reorderedXml = """
                <flowtran><interface id="TC049"><input>
                  <fields id="cards"><field id="number"/></fields>
                  <fields id="accounts"><field id="amount"/></fields>
                </input></interface></flowtran>
                """;

        assertEquals(parser.parse(firstXml).fields().keySet(), parser.parse(reorderedXml).fields().keySet());
    }

    @Test
    void rejects_duplicate_field_identity() {
        String xml = """
                <flowtran><interface id="TC050"><input>
                  <field id="same"/><field id="same"/>
                </input></interface></flowtran>
                """;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(xml));

        assertTrue(exception.getMessage().contains("Duplicate field identity"));
    }

    @Test
    void rejects_doctype_and_external_entities() {
        String xml = """
                <!DOCTYPE flowtran [<!ENTITY payload SYSTEM "file:///etc/passwd">]>
                <flowtran><interface id="TC051"><input><field id="&payload;"/></input></interface></flowtran>
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(xml));
    }
}
