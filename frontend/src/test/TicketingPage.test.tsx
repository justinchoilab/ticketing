import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import TicketingPage from '../features/ticketing/TicketingPage';

// WebSocket(STOMP) 모킹
vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(() => ({
    activate: vi.fn(),
    deactivate: vi.fn(),
    onConnect: null,
    onDisconnect: null,
    subscribe: vi.fn(),
  })),
}));

const mockFetch = vi.fn();
global.fetch = mockFetch;

const statusResponse = {
  name: '티켓팅 데모',
  totalCapacity: 100,
  reservedCount: 0,
  remaining: 100,
};

beforeEach(() => {
  mockFetch.mockResolvedValue({
    ok: true,
    json: async () => statusResponse,
  });
});

describe('TicketingPage', () => {
  it('제목이 렌더링된다', async () => {
    render(<TicketingPage />);
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });

  it('예매 버튼이 렌더링된다', async () => {
    render(<TicketingPage />);
    expect(await screen.findByRole('button', { name: /예매|티켓|reserve/i })).toBeInTheDocument();
  });

  it('리셋 버튼이 렌더링된다', async () => {
    render(<TicketingPage />);
    expect(await screen.findByRole('button', { name: /리셋|reset/i })).toBeInTheDocument();
  });

  it('사용자 ID 입력 필드가 있다', async () => {
    render(<TicketingPage />);
    expect(await screen.findByRole('textbox')).toBeInTheDocument();
  });
});
