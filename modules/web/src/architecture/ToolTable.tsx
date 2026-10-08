// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { argumentHint, firstSentence, type McpTool } from '../api/mcpTools';
import { t } from '../i18n';

/** The MCP server's tool registry as served, one row per tool in list order; the argument column only when a description names any. */
export function ToolTable({ tools }: { tools: McpTool[] }) {
  const hints = tools.map((t) => argumentHint(t.description));
  const withArgs = hints.some((h) => h !== null);
  return (
    <div id="arch-mcp-tools" className="arch-tools">
      <table className="arch-tools-table">
        <thead>
          <tr>
            <th scope="col">{t('architecture.tool-table.tool')}</th>
            <th scope="col">{t('architecture.tool-table.descriptionFirstSentence')}</th>
            {withArgs ? <th scope="col">{t('architecture.tool-table.arguments')}</th> : null}
          </tr>
        </thead>
        <tbody>
          {tools.map((t, i) => (
            <tr key={t.name}>
              <th scope="row" className="mono">{t.name}</th>
              <td>{firstSentence(t.description)}</td>
              {withArgs ? <td className="arch-tools-args">{hints[i]}</td> : null}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="arch-tools-foot">
        {t('architecture.tool-table.servedBy')} <span className="mono">{t('architecture.tool-table.getApiQueryMcpTools')}</span>, the same registry the MCP server exposes; any MCP client reads it with <span className="mono">{t('architecture.tool-table.toolsList')}</span>
      </p>
    </div>
  );
}
