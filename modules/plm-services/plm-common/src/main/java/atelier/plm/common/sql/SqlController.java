// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.catalogue.AnnotationCatalogue;
import atelier.plm.common.catalogue.Catalogue;
import atelier.plm.common.log.PurposeLog;
import atelier.plm.common.policy.Clearance;
import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.policy.Policy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Catalogue-grounded SQL over this PLM's native tables at POST /{plm}/sql, body
 * {@code { "sql": ..., "purpose": ... }}, profile header required. The statement is parsed and must
 * be one SELECT; every table and column must exist in this PLM's catalogue, or the answer is 400
 * with the closest catalogue names and nothing runs; each part or feature table reference is
 * rewritten to the releasability-filtered view {@code /{plm}/tables/{table}} serves the profile;
 * {@code LIMIT 200} is enforced; the statement runs read-only with a 5 s timeout, over the
 * service's own datasource (the PLM's application role, the only login the environment provides
 * for this database). The answer carries the SQL that actually ran, the rows, the elapsed time and
 * the catalogue entries used.
 */
@RestController
public class SqlController {

    private static final Logger log = LoggerFactory.getLogger(SqlController.class);

    private final EntityManager entityManager;
    private final Policy policy;
    private final PartTagStore tags;
    private final SqlRunner runner;
    private final String plm;

    public SqlController(EntityManager entityManager, DataSource dataSource, PlatformTransactionManager transactionManager,
                         Policy policy, PartTagStore tags, @Value("${plm.code}") String plmCode) {
        this.entityManager = entityManager;
        this.policy = policy;
        this.tags = tags;
        this.runner = new SqlRunner(dataSource, transactionManager);
        this.plm = plmCode.toUpperCase(Locale.ROOT);
    }

    @PostMapping("/${plm.code}/sql")
    public SqlResponse sql(@RequestBody SqlRequest request,
                           @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        if (profile == null || profile.isBlank()) {
            throw new SqlRejectedException(Policy.HEADER + " header is required", "missing-profile");
        }
        if (request.sql() == null || request.sql().isBlank()) {
            throw new SqlRejectedException("sql is required", "missing-sql");
        }
        long started = System.nanoTime();
        Clearance clearance = policy.clearance(profile);
        Metamodel metamodel = entityManager.getMetamodel();
        Catalogue catalogue = AnnotationCatalogue.fromMetamodel(plm, metamodel);

        Select select = SqlStatement.parse(request.sql());
        CatalogueWalker walker = new CatalogueWalker(catalogue);
        walker.analyse(select);
        ReleasabilityRewrite.Result rewrite = ReleasabilityRewrite.apply(walker.baseTables(), metamodel, clearance, tags);
        SqlStatement.clampLimit(select);
        String executed = select.toString();

        SqlRunner.Result result = runner.run(executed, rewrite.args());
        long ms = (System.nanoTime() - started) / 1_000_000;
        log.info("sql plm={} profile={} rows={} ms={} purpose={}", plm, clearance.profile(), result.rows().size(), ms, PurposeLog.safe(request.purpose()));
        return new SqlResponse(plm, clearance.profile(), request.sql(), executed, result.columns(), result.rows(),
                result.rows().size(), ms, walker.catalogueUsed(), rewrite.policy());
    }

    @ExceptionHandler(SqlRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SqlError rejected(SqlRejectedException e) {
        return e.toError();
    }

    /**
     * The database refused or timed out on a statement that passed the parser and the catalogue.
     * The answer names the SQLSTATE only: the driver message can quote values of rows the profile
     * may not see.
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SqlError failed(DataAccessException e) {
        String state = null;
        for (Throwable cause = e; cause != null && state == null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql) {
                state = sql.getSQLState();
            }
        }
        return new SqlError("the database rejected the statement" + (state == null ? "" : " (SQLSTATE " + state + ")"),
                "execution-error", List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SqlError unreadable(HttpMessageNotReadableException e) {
        return new SqlError("body must be JSON { \"sql\": ..., \"purpose\": ... }", "bad-request", List.of());
    }
}
