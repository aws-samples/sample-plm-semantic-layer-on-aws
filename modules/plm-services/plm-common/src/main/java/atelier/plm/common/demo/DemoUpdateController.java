// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.log.PurposeLog;
import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.policy.Policy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Value corrections at POST /{plm}/demo/update, one ({@code { table, key, column, value, purpose }})
 * or a batch ({@code { updates: [ { table, key, column, value }, ... ], purpose }}, see
 * {@link DemoUpdateRequest}): the {@value Policy#HEADER} header must be this PLM's own engineer or
 * the export-control officer (403 otherwise, see {@link DemoAccess}), and every correction is
 * checked against this PLM's catalogue (see {@link UpdatableColumn}) before any SQL runs, so a
 * refused one leaves the whole request unapplied. The rows are found by their table's key column and
 * the parameterised UPDATEs run over the service's own datasource in one 5 s transaction (see
 * {@link ValueUpdate}). Nothing is written to any graph: the next run reads the new values through
 * Ontop. Once the transaction has committed, one EventBridge event announces the request, source
 * {@value #SOURCE}, detail-type {@value #DETAIL_TYPE}, detail {@code { plm, rows: [ { table, key,
 * column, before, after } ], actor, purpose, at }} ({@code actor} the caller's profile); Atelier
 * subscribes to it to keep its change log. The answer is {@code { plm, rows, at, eventId }}. The
 * corrections stand without their announcement: a publisher without a bus, or one EventBridge
 * refuses, leaves {@code eventId} null and says why in {@code eventSkipped}.
 * <p>
 * The demo reset sets each corrected cell back to the before value the core change log recorded,
 * and a seeded cell can hold a value outside its column's words or form (a malformed URN is one of
 * the seeded defects). Such a value is accepted for one request only: the export-control officer's,
 * with a purpose starting {@value #RESET_PURPOSE}, for a cell whose logged correction
 * ({@link PartTagStore#loggedBefore}) has exactly that value as before. The log is read over the
 * core read-only connection, so the exception cannot be claimed by a request alone, and it can
 * only put back a value the cell held; every other correction keeps the column's words and form.
 */
@RestController
public class DemoUpdateController {

    static final String SOURCE = "atelier.plm";
    static final String DETAIL_TYPE = "part.value.corrected";
    static final String RESET_PURPOSE = "reset:";

    private static final Logger log = LoggerFactory.getLogger(DemoUpdateController.class);

    private final EntityManager entityManager;
    private final ValueUpdate update;
    private final EventPublisher publisher;
    private final ObjectMapper json;
    private final DemoAccess access;
    private final PartTagStore core;
    private final String plm;

    public DemoUpdateController(EntityManager entityManager, DataSource dataSource, PlatformTransactionManager transactionManager,
                                EventPublisher publisher, ObjectMapper json, PartTagStore core, @Value("${plm.code}") String plmCode) {
        this.entityManager = entityManager;
        this.update = new ValueUpdate(dataSource, transactionManager);
        this.publisher = publisher;
        this.json = json;
        this.access = DemoAccess.plm(plmCode);
        this.core = core;
        this.plm = plmCode.toUpperCase(Locale.ROOT);
    }

    @PostMapping("/${plm.code}/demo/update")
    public DemoUpdateResponse update(@RequestBody DemoUpdateRequest request,
                                     @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        access.require(profile);
        Metamodel metamodel = entityManager.getMetamodel();
        boolean reset = DemoAccess.OFFICER.equals(profile) && request.purpose() != null && request.purpose().startsWith(RESET_PURPOSE);
        List<ValueUpdate.Change> changes = new ArrayList<>();
        for (DemoUpdateRequest.Correction correction : request.corrections()) {
            UpdatableColumn column = UpdatableColumn.of(metamodel, correction.table(), correction.column());
            JsonNode value = correction.value();
            Object coerced = reset && value.isTextual() && core.loggedBefore(column.table(), correction.key(), column.column(), value.asText())
                    ? column.restore(value) : column.coerce(value);
            changes.add(new ValueUpdate.Change(column, correction.key(), column.key(correction.key()), coerced));
        }
        List<DemoUpdateResponse.Row> rows = update.apply(changes);
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plm", plm);
        detail.put("rows", rows);
        detail.put("actor", profile);
        detail.put("purpose", request.purpose());
        detail.put("at", at);
        String detailJson = toJson(detail);
        String skipped = publisher.skipped();
        String eventId = null;
        if (skipped == null) {
            try {
                eventId = publisher.put(SOURCE, DETAIL_TYPE, detailJson);
            } catch (RuntimeException e) {
                skipped = e instanceof DemoRejectedException ? e.getMessage() : "the event bus did not accept the event";
                log.warn("demo update event not published plm={} rows={}: {}", plm, rows.size(), e.toString());
            }
        }
        for (DemoUpdateResponse.Row row : rows) {
            log.info("demo update plm={} table={} key={} column={} before={} after={} purpose={} eventId={} skipped={}", plm, row.table(),
                    row.key(), row.column(), row.before(), row.after(), PurposeLog.safe(request.purpose()), eventId, skipped);
        }
        return new DemoUpdateResponse(plm, rows, at, eventId, skipped);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the event detail", e);
        }
    }

    /** The database refused the update (a value outside the column's range, a NULL in a NOT NULL column, a timeout); the answer names the SQLSTATE only. */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DemoError failed(DataAccessException e) {
        String state = null;
        for (Throwable cause = e; cause != null && state == null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql) {
                state = sql.getSQLState();
            }
        }
        return new DemoError("the database rejected the update" + (state == null ? "" : " (SQLSTATE " + state + ")"), "execution-error");
    }
}
