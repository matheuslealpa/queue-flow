import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('lê eventos no formato do SseEmitter do Spring (sem espaço após os dois-pontos)', () => {
    const parser = new SseParser();
    expect(parser.push('event:entry.called\ndata:{"ticket":"A001"}\n\n')).toEqual([
      { event: 'entry.called', data: '{"ticket":"A001"}' },
    ]);
  });

  it('junta mensagens partidas entre pedaços do fluxo', () => {
    const parser = new SseParser();
    expect(parser.push('event:ready\nretry:3000\nda')).toEqual([]);
    expect(parser.push('ta:abc\n\nevent:entry.created\n')).toEqual([{ event: 'ready', data: 'abc' }]);
    expect(parser.push('data: {"x":1}\r\n\r\n')).toEqual([{ event: 'entry.created', data: '{"x":1}' }]);
  });

  it('ignora o heartbeat (comentário)', () => {
    expect(new SseParser().push(':ping\n\n')).toEqual([]);
  });

  it('concatena várias linhas de data', () => {
    expect(new SseParser().push('data:a\ndata:b\n\n')).toEqual([{ event: 'message', data: 'a\nb' }]);
  });
});
