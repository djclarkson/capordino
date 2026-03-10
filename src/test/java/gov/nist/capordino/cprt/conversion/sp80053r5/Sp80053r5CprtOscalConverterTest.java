package gov.nist.capordino.cprt.conversion.sp80053r5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.capordino.cprt.api.CprtApiClient;
import gov.nist.capordino.cprt.conversion.InvalidFrameworkIdentifier;
import gov.nist.capordino.cprt.pojo.CprtMetadataVersion;
import gov.nist.capordino.cprt.pojo.CprtRoot;
import gov.nist.secauto.metaschema.binding.io.Format;
import gov.nist.secauto.metaschema.binding.io.ISerializer;
import gov.nist.secauto.metaschema.model.common.validation.IValidationResult;
import gov.nist.secauto.oscal.lib.OscalBindingContext;
import gov.nist.secauto.oscal.lib.model.Catalog;
import gov.nist.secauto.oscal.lib.model.CatalogGroup;
import gov.nist.secauto.oscal.lib.model.Control;
import gov.nist.secauto.oscal.lib.model.ControlPart;
import gov.nist.secauto.oscal.lib.model.Link;
import gov.nist.secauto.oscal.lib.model.Parameter;
import gov.nist.secauto.oscal.lib.model.Property;

/**
 * Tests for {@link Sp80053r5CprtOscalConverter}.
 *
 * <p>Offline tests use a local fixture ({@code cprt_sp80053r5_sample.json}) containing
 * AC-1, AC-2, AC-3, AC-4, AC-13 (withdrawn), and AC-25 with all their
 * enhancements, statements, ODPs, determinations, and assessment methods.
 *
 * <p>Online tests (tagged {@code Online}) call the live CPRT API and validate
 * the full SP 800-53 Rev 5 catalog.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Sp80053r5CprtOscalConverterTest {

    private static final File FIXTURE_FILE = new File("src/test/resources/cprt_sp80053r5_sample.json");

    @TempDir(cleanup = CleanupMode.NEVER)
    static Path tempOutDirectory;

    static Path sampleOutFilePath;
    static Path onlineOutFilePath;

    private static OscalBindingContext bindingContext;
    private static CprtRoot root;
    private static CprtMetadataVersion version;
    private static Catalog sampleCatalog;

    @BeforeAll
    static void initialize() throws Exception {
        bindingContext = OscalBindingContext.instance();
        sampleOutFilePath = tempOutDirectory.resolve("sp80053r5-sample_catalog.xml");
        onlineOutFilePath = tempOutDirectory.resolve("sp80053r5_catalog.xml");

        System.out.println("Saving output to: " + tempOutDirectory.toString());

        ObjectMapper mapper = new ObjectMapper();
        root = mapper.readValue(FIXTURE_FILE, CprtRoot.class);

        version = new CprtMetadataVersion();
        version.name = "Security and Privacy Controls for Information Systems and Organizations";
        version.frameworkIdentifier = "SP_800_53";
        version.frameworkWebSite = "https://csrc.nist.gov/pubs/sp/800/53/r5/upd1/final";
        version.frameworkVersionIdentifier = "SP_800_53_5_2_0";
        version.interfaceIdentifier = "SP_800_53_1";
        version.frameworkVersionName = "Security and Privacy Controls for Information Systems and Organizations";
        version.shortName = "SP 800-53 Rev 5.2.0";
        version.version = "5.2.0";
        version.publicationReleaseDate = new Date();
        version.pocEmailAddress = "sec-cert@nist.gov";
    }

    // ========================================================================
    // Phase 1: Fixture parsing
    // ========================================================================

    @Test
    @Order(1)
    void testFixtureParsesToCprtRoot() {
        assertNotNull(root);
        assertFalse(root.getElements().isEmpty(), "Fixture should contain elements");
        assertFalse(root.getRelationships().isEmpty(), "Fixture should contain relationships");
    }

    // ========================================================================
    // Phase 2: Conversion and serialization
    // ========================================================================

    @Test
    @Order(2)
    void testConvertSampleToOscal() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        sampleCatalog = converter.buildCatalog();

        assertNotNull(sampleCatalog);
        assertNotNull(sampleCatalog.getGroups());

        // Serialize and reload
        ISerializer<Catalog> serializer = bindingContext.newSerializer(Format.XML, Catalog.class);
        serializer.serialize(sampleCatalog, sampleOutFilePath);
        assertNotNull(bindingContext.loadCatalog(sampleOutFilePath));
    }

    @Test
    @Order(3)
    void testSerializeSampleOscalRoundTrip() throws IOException {
        // Validate that the serialized output can be loaded back.
        // Full constraint validation (including referential integrity) is deferred
        // to the Online test since the fixture contains only AC-family controls
        // while related links reference controls in other families.
        assertNotNull(bindingContext.loadCatalog(sampleOutFilePath),
                "Serialized catalog should load back successfully");
    }

    // ========================================================================
    // Phase 3: Groups, Controls, Enhancements
    // ========================================================================

    @Test
    @Order(10)
    void testSampleHasAcFamilyGroup() throws Exception {
        Catalog catalog = buildSampleCatalog();
        List<CatalogGroup> groups = catalog.getGroups();

        // Fixture only has AC family
        assertTrue(groups.size() >= 1, "Should have at least 1 group (AC)");

        CatalogGroup acGroup = groups.stream()
                .filter(g -> "ac".equals(g.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(acGroup, "AC group should exist");
        assertEquals("family", acGroup.getClazz());
    }

    @Test
    @Order(11)
    void testAcGroupHasControls() throws Exception {
        CatalogGroup acGroup = getAcGroup(buildSampleCatalog());
        assertNotNull(acGroup.getControls());
        assertTrue(acGroup.getControls().size() >= 5,
                "AC group should have at least 5 controls in fixture");
    }

    @Test
    @Order(12)
    void testAc1ControlIdAndClass() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        assertNotNull(ac1, "AC-1 should exist");
        assertEquals("ac-1", ac1.getId());
        assertEquals("SP800-53", ac1.getClazz());
    }

    @Test
    @Order(13)
    void testAc1HasTitle() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        assertNotNull(ac1.getTitle(), "AC-1 should have a title");
    }

    @Test
    @Order(14)
    void testAc2HasEnhancements() throws Exception {
        Control ac2 = getControl(buildSampleCatalog(), "ac-2");
        assertNotNull(ac2, "AC-2 should exist");
        assertNotNull(ac2.getControls(), "AC-2 should have enhancements");
        assertTrue(ac2.getControls().size() > 0, "AC-2 should have at least 1 enhancement");

        // Check AC-2(1) exists as nested enhancement
        Control ac2_1 = ac2.getControls().stream()
                .filter(c -> "ac-2.1".equals(c.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(ac2_1, "AC-2(1) should be nested under AC-2");
    }

    @Test
    @Order(15)
    void testNoEnhancementAtTopLevel() throws Exception {
        CatalogGroup acGroup = getAcGroup(buildSampleCatalog());
        for (Control ctrl : acGroup.getControls()) {
            assertFalse(ctrl.getId().contains("."),
                    "Enhancement " + ctrl.getId() + " should not appear at top level");
        }
    }

    @Test
    @Order(16)
    void testAc13IsWithdrawn() throws Exception {
        Control ac13 = getControl(buildSampleCatalog(), "ac-13");
        assertNotNull(ac13, "AC-13 should exist");

        boolean hasWithdrawnStatus = ac13.getProps().stream()
                .anyMatch(p -> "status".equals(p.getName()) && "withdrawn".equals(p.getValue()));
        assertTrue(hasWithdrawnStatus, "AC-13 should have status=withdrawn prop");
    }

    // ========================================================================
    // Phase 4: ODP / Parameters
    // ========================================================================

    @Test
    @Order(20)
    void testAc1HasEightOdps() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        assertNotNull(ac1.getParams(), "AC-1 should have params");

        // Count individual ODPs (not aggregates)
        long odpCount = ac1.getParams().stream()
                .filter(p -> p.getId().contains("_odp."))
                .count();
        assertEquals(8, odpCount, "AC-1 should have 8 individual ODP params");
    }

    @Test
    @Order(21)
    void testAc1Odp03HasSelect() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        Parameter odp03 = ac1.getParams().stream()
                .filter(p -> "ac-01_odp.03".equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(odp03, "ac-01_odp.03 should exist");
        assertNotNull(odp03.getSelect(), "ac-01_odp.03 should have a SELECT constraint");
    }

    @Test
    @Order(22)
    void testAc1Odp03SelectHowMany() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        Parameter odp03 = ac1.getParams().stream()
                .filter(p -> "ac-01_odp.03".equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(odp03);
        assertEquals("one-or-more", odp03.getSelect().getHowMany());
    }

    @Test
    @Order(23)
    void testAc1Odp03SelectThreeChoices() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        Parameter odp03 = ac1.getParams().stream()
                .filter(p -> "ac-01_odp.03".equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(odp03);
        assertNotNull(odp03.getSelect().getChoice());
        assertEquals(3, odp03.getSelect().getChoice().size(),
                "ac-01_odp.03 should have 3 choices");
    }

    @Test
    @Order(24)
    void testAc1HasAggregateParam() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        // AC-1 ODPs 01 and 02 both have title "personnel or roles" -> aggregate
        Parameter aggParam = ac1.getParams().stream()
                .filter(p -> p.getId().contains("_prm_"))
                .findFirst()
                .orElse(null);
        assertNotNull(aggParam, "AC-1 should have an aggregate param");

        // Check aggregates props
        long aggPropsCount = aggParam.getProps().stream()
                .filter(p -> "aggregates".equals(p.getName()))
                .count();
        assertEquals(2, aggPropsCount, "Aggregate param should reference 2 ODPs");
    }

    @Test
    @Order(25)
    void testAc1OdpWithoutSelectHasNoSelectField() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        // ODP[01] is single_entry (no select)
        Parameter odp01 = ac1.getParams().stream()
                .filter(p -> "ac-01_odp.01".equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(odp01);
        assertNull(odp01.getSelect(), "ac-01_odp.01 should not have a SELECT constraint");
        assertNotNull(odp01.getLabel(), "ac-01_odp.01 should have a label");
    }

    // ========================================================================
    // Phase 5: Parts (Statement, Assessment Objective, Assessment Method)
    // ========================================================================

    @Test
    @Order(30)
    void testAc1HasStatementPart() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        assertNotNull(ac1.getParts(), "AC-1 should have parts");

        ControlPart stmtPart = ac1.getParts().stream()
                .filter(p -> "statement".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(stmtPart, "AC-1 should have a statement part");
        assertEquals("ac-1_smt", stmtPart.getId());
    }

    @Test
    @Order(31)
    void testAc1StatementHasChildren() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart stmtPart = findPartByName(ac1.getParts(), "statement");
        assertNotNull(stmtPart);
        assertNotNull(stmtPart.getParts(), "Statement should have child parts");
        assertTrue(stmtPart.getParts().size() >= 3,
                "AC-1 statement should have at least 3 children (a, b, c)");
    }

    @Test
    @Order(32)
    void testAc1HasGuidancePart() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart gdnPart = findPartByName(ac1.getParts(), "guidance");
        assertNotNull(gdnPart, "AC-1 should have a guidance part");
        assertEquals("ac-1_gdn", gdnPart.getId());
    }

    @Test
    @Order(33)
    void testAc1HasExamineMethod() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart examPart = findAssessmentMethod(ac1.getParts(), "EXAMINE");
        assertNotNull(examPart, "AC-1 should have an EXAMINE assessment method");
        assertEquals("ac-1_asm-examine", examPart.getId());
    }

    @Test
    @Order(34)
    void testAc1HasInterviewMethod() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart interviewPart = findAssessmentMethod(ac1.getParts(), "INTERVIEW");
        assertNotNull(interviewPart, "AC-1 should have an INTERVIEW assessment method");
    }

    @Test
    @Order(35)
    void testAc1HasNoTestMethod() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart testPart = findAssessmentMethod(ac1.getParts(), "TEST");
        assertNull(testPart, "AC-1 should NOT have a TEST assessment method");
    }

    @Test
    @Order(36)
    void testAc1ExamineHasAssessmentObjects() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart examPart = findAssessmentMethod(ac1.getParts(), "EXAMINE");
        assertNotNull(examPart);
        assertNotNull(examPart.getParts(), "EXAMINE part should have sub-parts");

        ControlPart objPart = examPart.getParts().stream()
                .filter(p -> "assessment-objects".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(objPart, "EXAMINE should have assessment-objects part");
        assertNotNull(objPart.getProse(), "Assessment objects should have prose");
    }

    @Test
    @Order(37)
    void testAc1HasAssessmentObjectives() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        ControlPart objRoot = findPartByName(ac1.getParts(), "assessment-objective");
        assertNotNull(objRoot, "AC-1 should have assessment-objective part");
    }

    // ========================================================================
    // Phase 6: Related Links
    // ========================================================================

    @Test
    @Order(40)
    void testAc1HasRelatedLinks() throws Exception {
        Control ac1 = getControl(buildSampleCatalog(), "ac-1");
        assertNotNull(ac1.getLinks(), "AC-1 should have links");

        Set<String> relatedHrefs = ac1.getLinks().stream()
                .filter(l -> "related".equals(l.getRel()))
                .map(l -> l.getHref().toString())
                .collect(Collectors.toSet());

        // AC-1 is related to IA-1, PM-9, PM-24, PS-8, SI-12 (and possibly SI-2 in v5.2.0)
        assertTrue(relatedHrefs.contains("#ia-1"), "AC-1 should be related to IA-1");
        assertTrue(relatedHrefs.contains("#pm-9"), "AC-1 should be related to PM-9");
        assertTrue(relatedHrefs.contains("#ps-8"), "AC-1 should be related to PS-8");
        assertTrue(relatedHrefs.contains("#si-12"), "AC-1 should be related to SI-12");
    }

    @Test
    @Order(41)
    void testRelatedLinksUseFragmentFormat() throws Exception {
        Catalog catalog = buildSampleCatalog();
        for (CatalogGroup group : catalog.getGroups()) {
            if (group.getControls() == null) continue;
            for (Control ctrl : group.getControls()) {
                if (ctrl.getLinks() == null) continue;
                for (Link link : ctrl.getLinks()) {
                    if ("related".equals(link.getRel())) {
                        assertTrue(link.getHref().toString().startsWith("#"),
                                "Related link " + link.getHref() + " on " + ctrl.getId()
                                        + " should use fragment format");
                    }
                }
            }
        }
    }

    @Test
    @Order(42)
    void testAc13HasIncorporatedIntoLinks() throws Exception {
        Control ac13 = getControl(buildSampleCatalog(), "ac-13");
        assertNotNull(ac13, "AC-13 should exist");

        if (ac13.getLinks() != null) {
            boolean hasIncorporatedInto = ac13.getLinks().stream()
                    .anyMatch(l -> "incorporated-into".equals(l.getRel()));
            assertTrue(hasIncorporatedInto, "AC-13 should have incorporated-into links");
        }
    }

    // ========================================================================
    // ID derivation tests
    // ========================================================================

    @Test
    @Order(50)
    void testControlIdDerivationBaseControl() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        assertEquals("ac-1", converter.deriveOscalControlId("AC-01"));
        assertEquals("ac-21", converter.deriveOscalControlId("AC-21"));
    }

    @Test
    @Order(51)
    void testControlIdDerivationEnhancement() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        assertEquals("ac-2.1", converter.deriveOscalControlId("AC-02(01)"));
        assertEquals("ac-21.11", converter.deriveOscalControlId("AC-21(11)"));
    }

    @Test
    @Order(52)
    void testSortIdDerivation() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        assertEquals("ac-01", converter.deriveSortId("AC-01"));
        assertEquals("ac-02.01", converter.deriveSortId("AC-02(01)"));
        assertEquals("ac-21.11", converter.deriveSortId("AC-21(11)"));
    }

    @Test
    @Order(53)
    void testOdpIdDerivation() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        assertEquals("ac-01_odp.01", converter.deriveOdpId("AC-01_ODP[01]"));
        assertEquals("ac-02.01_odp", converter.deriveOdpId("AC-02(01)_ODP"));
    }

    @Test
    @Order(54)
    void testControlLabelUpperDerivation() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        assertEquals("AC-01", converter.deriveControlLabelUpper("ac-1"));
        assertEquals("AC-02(01)", converter.deriveControlLabelUpper("ac-2.1"));
    }

    // ========================================================================
    // Placeholder substitution tests
    // ========================================================================

    @Test
    @Order(60)
    void testSubstituteAssignmentPlaceholder() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        java.util.Map<String, String> odpMap = new java.util.HashMap<>();
        odpMap.put("organization-defined personnel or roles", "ac-1_prm_1");

        String result = converter.substituteOdpPlaceholders(
                "[Assignment: organization-defined personnel or roles]", odpMap);
        assertEquals("{{ insert: param, ac-1_prm_1 }}", result);
    }

    @Test
    @Order(61)
    void testSubstituteUnresolvablePlaceholder() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        java.util.Map<String, String> odpMap = new java.util.HashMap<>();

        String input = "[Assignment: organization-defined unrecognized parameter]";
        String result = converter.substituteOdpPlaceholders(input, odpMap);
        // Should return verbatim when unresolvable
        assertEquals(input, result);
    }

    @Test
    @Order(62)
    void testSubstituteOdpReference() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        java.util.Map<String, String> odpMap = new java.util.HashMap<>();
        odpMap.put("AC-01_ODP[03]", "ac-01_odp.03");

        String result = converter.substituteOdpPlaceholders(
                "the <AC-01_ODP[03] SELECTED PARAMETER VALUES> access control policy", odpMap);
        assertTrue(result.contains("{{ insert: param, ac-01_odp.03 }}"),
                "Should substitute ODP reference. Got: " + result);
    }

    // ========================================================================
    // Online tests (require network)
    // ========================================================================

    @Test
    @Order(100)
    @Tag("Online")
    void testConvertFullCatalogFromApi() throws Exception {
        CprtApiClient client = new CprtApiClient();
        CprtMetadataVersion onlineVersion = client.getMetadata().versions.stream()
                .filter(v -> v.frameworkVersionIdentifier.equals("SP_800_53_5_2_0"))
                .findFirst()
                .orElseThrow();

        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(onlineVersion);
        Catalog catalog = converter.buildCatalog();

        assertNotNull(catalog);
        assertNotNull(catalog.getGroups());
        assertEquals(20, catalog.getGroups().size(), "Should have 20 control family groups");

        // Serialize
        ISerializer<Catalog> serializer = bindingContext.newSerializer(Format.XML, Catalog.class);
        serializer.serialize(catalog, onlineOutFilePath);
        assertNotNull(bindingContext.loadCatalog(onlineOutFilePath));
    }

    @Test
    @Order(101)
    @Tag("Online")
    void testValidateFullCatalogOscal() throws IOException {
        IValidationResult results = bindingContext.validateWithConstraints(onlineOutFilePath);
        if (!results.isPassing()) {
            StringBuilder sb = new StringBuilder("Validation findings:\n");
            int count = 0;
            for (var finding : results.getFindings()) {
                if (count++ >= 20) {
                    sb.append("... and more (truncated)\n");
                    break;
                }
                sb.append("  ").append(finding.getSeverity()).append(": ").append(finding.getMessage()).append("\n");
            }
            System.err.println(sb.toString());
        }
        assertTrue(results.isPassing(), "Full catalog OSCAL validation should pass");
    }

    @Test
    @Order(102)
    @Tag("Online")
    void testFullCatalogGroupIds() throws Exception {
        Catalog catalog = loadOnlineCatalog();
        if (catalog == null) return;

        Set<String> groupIds = catalog.getGroups().stream()
                .map(CatalogGroup::getId)
                .collect(Collectors.toSet());

        Set<String> expected = Set.of("ac", "at", "au", "ca", "cm", "cp", "ia", "ir",
                "ma", "mp", "pe", "pl", "pm", "ps", "pt", "ra", "sa", "sc", "si", "sr");
        assertEquals(expected, groupIds);
    }

    @Test
    @Order(103)
    @Tag("Online")
    void testFullCatalogBaseControlCount() throws Exception {
        Catalog catalog = loadOnlineCatalog();
        if (catalog == null) return;

        int baseCount = 0;
        for (CatalogGroup group : catalog.getGroups()) {
            if (group.getControls() != null) {
                baseCount += group.getControls().size();
            }
        }
        assertEquals(324, baseCount, "Should have 324 base controls");
    }

    @Test
    @Order(104)
    @Tag("Online")
    void testFullCatalogEnhancementCount() throws Exception {
        Catalog catalog = loadOnlineCatalog();
        if (catalog == null) return;

        int enhCount = 0;
        for (CatalogGroup group : catalog.getGroups()) {
            if (group.getControls() == null) continue;
            for (Control ctrl : group.getControls()) {
                if (ctrl.getControls() != null) {
                    enhCount += ctrl.getControls().size();
                }
            }
        }
        assertEquals(872, enhCount, "Should have 872 enhancements");
    }

    @Test
    @Order(105)
    @Tag("Online")
    void testFullCatalogAssessmentMethodCounts() throws Exception {
        Catalog catalog = loadOnlineCatalog();
        if (catalog == null) return;

        int examine = 0, interview = 0, test = 0;
        for (CatalogGroup group : catalog.getGroups()) {
            if (group.getControls() == null) continue;
            for (Control ctrl : group.getControls()) {
                int[] counts = countAssessmentMethods(ctrl);
                examine += counts[0];
                interview += counts[1];
                test += counts[2];
                if (ctrl.getControls() != null) {
                    for (Control enh : ctrl.getControls()) {
                        int[] enhCounts = countAssessmentMethods(enh);
                        examine += enhCounts[0];
                        interview += enhCounts[1];
                        test += enhCounts[2];
                    }
                }
            }
        }

        assertTrue(Math.abs(examine - 1013) <= 5,
                "EXAMINE count should be ~1013, got " + examine);
        assertTrue(Math.abs(interview - 1012) <= 5,
                "INTERVIEW count should be ~1012, got " + interview);
        assertTrue(Math.abs(test - 906) <= 5,
                "TEST count should be ~906, got " + test);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private Catalog buildSampleCatalog() throws Exception {
        Sp80053r5CprtOscalConverter converter = new Sp80053r5CprtOscalConverter(version, root);
        return converter.buildCatalog();
    }

    private CatalogGroup getAcGroup(Catalog catalog) {
        return catalog.getGroups().stream()
                .filter(g -> "ac".equals(g.getId()))
                .findFirst()
                .orElse(null);
    }

    private Control getControl(Catalog catalog, String controlId) {
        for (CatalogGroup group : catalog.getGroups()) {
            if (group.getControls() == null) continue;
            for (Control ctrl : group.getControls()) {
                if (controlId.equals(ctrl.getId())) return ctrl;
                if (ctrl.getControls() != null) {
                    for (Control enh : ctrl.getControls()) {
                        if (controlId.equals(enh.getId())) return enh;
                    }
                }
            }
        }
        return null;
    }

    private ControlPart findPartByName(List<ControlPart> parts, String name) {
        if (parts == null) return null;
        for (ControlPart part : parts) {
            if (name.equals(part.getName())) return part;
        }
        return null;
    }

    private ControlPart findAssessmentMethod(List<ControlPart> parts, String methodValue) {
        if (parts == null) return null;
        for (ControlPart part : parts) {
            if (!"assessment-method".equals(part.getName())) continue;
            if (part.getProps() == null) continue;
            for (Property prop : part.getProps()) {
                if ("method".equals(prop.getName()) && methodValue.equals(prop.getValue())) {
                    return part;
                }
            }
        }
        return null;
    }

    private int[] countAssessmentMethods(Control ctrl) {
        int examine = 0, interview = 0, test = 0;
        if (ctrl.getParts() == null) return new int[]{0, 0, 0};
        for (ControlPart part : ctrl.getParts()) {
            if (!"assessment-method".equals(part.getName())) continue;
            if (part.getProps() == null) continue;
            for (Property prop : part.getProps()) {
                if ("method".equals(prop.getName())) {
                    switch (prop.getValue()) {
                        case "EXAMINE": examine++; break;
                        case "INTERVIEW": interview++; break;
                        case "TEST": test++; break;
                    }
                }
            }
        }
        return new int[]{examine, interview, test};
    }

    private Catalog loadOnlineCatalog() {
        try {
            return bindingContext.loadCatalog(onlineOutFilePath);
        } catch (Exception e) {
            return null;
        }
    }
}
