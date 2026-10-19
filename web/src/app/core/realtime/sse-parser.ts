export interface SseMessage {
  event: string;
  data: string;
}

/**
 * Lê um fluxo `text/event-stream` (formato do EventSource): blocos separados por linha em branco,
 * campos `event:` e `data:`, comentários iniciados por `:` (o heartbeat da API).
 */
export class SseParser {
  private buffer = '';

  /** Recebe um pedaço do fluxo e devolve as mensagens completas que ele fechou. */
  push(chunk: string): SseMessage[] {
    this.buffer += chunk;
    const blocks = this.buffer.split(/\r?\n\r?\n/);
    this.buffer = blocks.pop() ?? '';
    return blocks.map(parseBlock).filter((m): m is SseMessage => m !== null);
  }
}

function parseBlock(block: string): SseMessage | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length || event !== 'message' ? { event, data: data.join('\n') } : null;
}
