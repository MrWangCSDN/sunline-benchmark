package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshotParser.DeterministicContentException;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.ParserConfigurationException;
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
    void ignores_fields_outside_the_interface_input_and_output_trees() {
        String xml = """
                <flowtran>
                  <field id="before-interface"/>
                  <interface id="TC045">
                    <metadata><field id="metadata-field"/></metadata>
                    <input><field id="request-field"/></input>
                    <output><field id="response-field"/></output>
                    <field id="after-output"/>
                  </interface>
                  <input><field id="outside-interface"/></input>
                </flowtran>
                """;

        FlowtransInterfaceSnapshot snapshot = parser.parse(xml);

        assertEquals(2, snapshot.fields().size());
        assertTrue(snapshot.fields().containsKey(new FieldIdentity("input", "/", "request-field")));
        assertTrue(snapshot.fields().containsKey(new FieldIdentity("output", "/", "response-field")));
    }

    @Test
    void captures_field_nested_beneath_a_field_with_its_container_path() {
        String xml = """
                <flowtran><interface id="TC045"><input>
                  <field id="parent"><fields id="nested"><field id="child"/></fields></field>
                </input></interface></flowtran>
                """;

        FlowtransInterfaceSnapshot snapshot = parser.parse(xml);

        assertTrue(snapshot.fields().containsKey(
                new FieldIdentity("input", "/fields[nested]", "child")));
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

        DeterministicContentException exception = assertThrows(
                DeterministicContentException.class, () -> parser.parse(xml));

        assertTrue(exception.getMessage().contains("Duplicate field identity"));
    }

    @Test
    void missing_interface_and_blank_field_id_are_deterministic_content_failures() {
        DeterministicContentException missingInterface = assertThrows(
                DeterministicContentException.class,
                () -> parser.parse("<flowtran><input/></flowtran>"));
        DeterministicContentException blankFieldId = assertThrows(
                DeterministicContentException.class,
                () -> parser.parse("""
                        <flowtran><interface id="TC052"><input><field id=" "/></input>
                        </interface></flowtran>
                        """));

        assertEquals("Missing interface element", missingInterface.getMessage());
        assertEquals("Field id must be nonblank", blankFieldId.getMessage());
    }

    @Test
    void duplicate_identity_and_malformed_xml_have_deterministic_safe_failures() {
        String duplicate = """
                <flowtran><interface id="TC050"><input>
                  <field id="same"/><field id="same"/>
                </input></interface></flowtran>
                """;
        String malformed = "<flowtran><interface id=\"TC051\"><input><field id=\"broken\"></flowtran>";

        DeterministicContentException firstDuplicate = assertThrows(
                DeterministicContentException.class, () -> parser.parse(duplicate));
        DeterministicContentException secondDuplicate = assertThrows(
                DeterministicContentException.class, () -> parser.parse(duplicate));
        DeterministicContentException firstMalformed = assertThrows(
                DeterministicContentException.class, () -> parser.parse(malformed));
        DeterministicContentException secondMalformed = assertThrows(
                DeterministicContentException.class, () -> parser.parse(malformed));

        assertEquals(firstDuplicate.getMessage(), secondDuplicate.getMessage());
        assertEquals("Unable to parse flowtrans interface snapshot", firstMalformed.getMessage());
        assertEquals(firstMalformed.getMessage(), secondMalformed.getMessage());
        assertFalse(firstMalformed.getMessage().contains("broken"));
    }

    @Test
    void rejects_doctype_and_external_entities() {
        String xml = """
                <!DOCTYPE flowtran [<!ENTITY payload SYSTEM "file:///etc/passwd">]>
                <flowtran><interface id="TC051"><input><field id="&payload;"/></input></interface></flowtran>
                """;

        assertThrows(DeterministicContentException.class, () -> parser.parse(xml));
    }

    @Test
    void parser_configuration_and_runtime_setup_failures_are_not_content_failures() {
        FlowtransInterfaceSnapshotParser configurationFailure = new FlowtransInterfaceSnapshotParser(
                () -> {
                    throw new ParserConfigurationException("secure parser feature is unsupported");
                });
        FlowtransInterfaceSnapshotParser runtimeFailure = new FlowtransInterfaceSnapshotParser(
                () -> {
                    throw new UnsupportedOperationException("secure parser runtime defect");
                });

        IllegalStateException configurationError = assertThrows(
                IllegalStateException.class, () -> configurationFailure.parse("<flowtran/>"));
        UnsupportedOperationException runtimeError = assertThrows(
                UnsupportedOperationException.class, () -> runtimeFailure.parse("<flowtran/>"));

        assertFalse(DeterministicContentException.class.isInstance(configurationError));
        assertFalse(DeterministicContentException.class.isInstance(runtimeError));
        assertTrue(configurationError.getCause() instanceof ParserConfigurationException);
    }
}
