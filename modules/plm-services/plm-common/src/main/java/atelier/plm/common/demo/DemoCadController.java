// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.policy.Policy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A CAD publication at POST /{plm}/demo/events/cad, body {@code { part, cadFile }}: the native id
 * of a part of this PLM (404 when the part table has no such row, see {@link PartTable}) and the
 * key of its STEP file, {@code cad/<product>/<name>.stp} (the product folder, then the file, lower
 * case), for this PLM's own engineer or the export-control
 * officer (403 otherwise, see {@link DemoAccess}). It puts one EventBridge event, source {@value #SOURCE}, detail-type
 * {@value #DETAIL_TYPE}, detail {@code { plm, part, partIri, cadFile, at }}, on the bus of
 * {@link EventPublisherConfiguration}; the links loader sets {@code atelier:cadFile} on the part IRI in
 * the file index. The answer is the event id with the detail.
 */
@RestController
public class DemoCadController {

    static final String SOURCE = "atelier.plm";
    static final String DETAIL_TYPE = "part.cad.published";
    static final Pattern CAD_FILE = Pattern.compile("cad/[a-z0-9-]+/[a-z0-9-]+\\.stp");

    private static final Logger log = LoggerFactory.getLogger(DemoCadController.class);

    private final EventPublisher publisher;
    private final ObjectMapper json;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final DemoAccess access;
    private final String plm;
    private final String plmCode;

    public DemoCadController(EventPublisher publisher, ObjectMapper json, EntityManager entityManager, JdbcTemplate jdbc,
                             @Value("${plm.code}") String plmCode) {
        this.publisher = publisher;
        this.json = json;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.access = DemoAccess.plm(plmCode);
        this.plm = plmCode.toUpperCase(Locale.ROOT);
        this.plmCode = plmCode.toLowerCase(Locale.ROOT);
    }

    @PostMapping("/${plm.code}/demo/events/cad")
    public CadEventResponse publish(@RequestBody CadEventRequest request,
                                    @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        access.require(profile);
        request.requireFields();
        if (!CAD_FILE.matcher(request.cadFile()).matches()) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "cadFile must match " + CAD_FILE.pattern() + ": " + request.cadFile(),
                    "bad-cad-file");
        }
        PartTable parts = PartTable.of(entityManager.getMetamodel())
                .orElseThrow(() -> new DemoRejectedException(HttpStatus.NOT_FOUND, "this service has no part table", "no-part-table"));
        if (!parts.has(jdbc, request.part())) {
            throw new DemoRejectedException(HttpStatus.NOT_FOUND,
                    "no row of " + parts.table() + " has " + parts.keyColumn() + " = " + request.part(), "part-not-found");
        }
        String partIri = PartIri.of(plmCode, request.part());
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plm", plm);
        detail.put("part", request.part());
        detail.put("partIri", partIri);
        detail.put("cadFile", request.cadFile());
        detail.put("at", at);
        String eventId = publisher.put(SOURCE, DETAIL_TYPE, toJson(detail));
        log.info("demo cad plm={} part={} cadFile={} eventId={}", plm, request.part(), request.cadFile(), eventId);
        return new CadEventResponse(eventId, plm, request.part(), partIri, request.cadFile(), at);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the event detail", e);
        }
    }
}
