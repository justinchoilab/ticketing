import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import styles from "./TicketingPage.module.css";
import { API_BASE_URL, WS_BASE_URL } from "../../config";
import LoadingButton from "../../components/LoadingButton";

type ReserveResult = "SUCCESS" | "DUPLICATE" | "SOLD_OUT" | "TIMEOUT" | "ERROR";

interface TicketingStatus {
  name: string;
  totalCapacity: number;
  reservedCount: number;
  remaining: number;
}

interface ReserveResponse {
  result: ReserveResult;
  userId: string;
  receivedAt: string;
  message: string;
}

interface LogEntry {
  id: number;
  result: ReserveResult;
  json: string;
}

interface BurstStats {
  success: number;
  duplicate: number;
  soldOut: number;
  timeout: number;
  error: number;
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("auth_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function fetchStatus(): Promise<TicketingStatus> {
  const res = await fetch(`${API_BASE_URL}/ticketing/status`, {
    headers: authHeaders(),
  });
  if (!res.ok) throw new Error("상태 조회 실패");
  return res.json();
}

async function postReserve(userId: string): Promise<ReserveResponse> {
  const res = await fetch(`${API_BASE_URL}/ticketing/reserve`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId }),
  });
  if (!res.ok) throw new Error("예매 요청 실패");
  return res.json();
}

async function postReset(): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/ticketing/reset`, {
    method: "POST",
    headers: authHeaders(),
  });
  if (!res.ok) throw new Error("리셋 실패");
}

let logIdCounter = 0;


const LOG_CLASS: Record<ReserveResult, string> = {
  SUCCESS:   "logOk",
  DUPLICATE: "logWarn",
  SOLD_OUT:  "logErr",
  TIMEOUT:   "logTimeout",
  ERROR:     "logErr",
};

export default function TicketingPage() {
  const [status, setStatus] = useState<TicketingStatus | null>(null);
  const [statusLoaded, setStatusLoaded] = useState(false);
  const [userId, setUserId] = useState("");
  const [reserving, setReserving] = useState(false);
  const [burstCount, setBurstCount] = useState(50);
  const [bursting, setBursting] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [stats, setStats] = useState<BurstStats>({ success: 0, duplicate: 0, soldOut: 0, timeout: 0, error: 0 });
  const [resetting, setResetting] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const consoleRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout>;

    const tryLoad = async () => {
      try {
        const s = await fetchStatus();
        if (!cancelled) { setStatus(s); setStatusLoaded(true); }
      } catch {
        if (!cancelled) { setStatusLoaded(true); retryTimer = setTimeout(tryLoad, 3000); }
      }
    };
    tryLoad();

    const token = localStorage.getItem("auth_token");
    const client = new Client({
      brokerURL: `${WS_BASE_URL}/ws`,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        client.subscribe("/topic/ticketing/status", msg => {
          setStatus(JSON.parse(msg.body));
        });
      },
    });
    client.activate();
    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      client.deactivate();
    };
  }, []);

  useEffect(() => {
    if (consoleRef.current) consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
  }, [logs]);

  const pushLog = (result: ReserveResult, res: ReserveResponse) => {
    const entry: LogEntry = {
      id: logIdCounter++,
      result,
      json: JSON.stringify(res),
    };
    setLogs(prev => [...prev, entry].slice(-200));
  };

  const addStat = (result: ReserveResult) => {
    setStats(prev => ({
      ...prev,
      success:   prev.success   + (result === "SUCCESS"   ? 1 : 0),
      duplicate: prev.duplicate + (result === "DUPLICATE" ? 1 : 0),
      soldOut:   prev.soldOut   + (result === "SOLD_OUT"  ? 1 : 0),
      timeout:   prev.timeout   + (result === "TIMEOUT"   ? 1 : 0),
      error:     prev.error     + (result === "ERROR"     ? 1 : 0),
    }));
  };

  const handleReserve = async () => {
    const uid = userId.trim();
    if (!uid) return;
    setReserving(true);
    try {
      const res = await postReserve(uid);
      pushLog(res.result, res);
      addStat(res.result);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "오류";
      pushLog("ERROR", { result: "ERROR", userId: uid, receivedAt: "", message: msg });
      addStat("ERROR");
    } finally {
      setReserving(false);
    }
  };

  const handleBurst = async () => {
    setBursting(true);
    const newEntries: LogEntry[] = [];
    const batchStats: BurstStats = { success: 0, duplicate: 0, soldOut: 0, timeout: 0, error: 0 };

    const runId = Date.now();
    await Promise.all(
      Array.from({ length: burstCount }, (_, i) => {
        const uid = `burst_${runId}_${String(i + 1).padStart(3, "0")}`;
        return postReserve(uid)
          .then(res => {
            if (res.result === "SUCCESS")        batchStats.success++;
            else if (res.result === "DUPLICATE") batchStats.duplicate++;
            else if (res.result === "SOLD_OUT")  batchStats.soldOut++;
            else if (res.result === "TIMEOUT")   batchStats.timeout++;
            else                                  batchStats.error++;
            newEntries.push({ id: logIdCounter++, result: res.result, json: JSON.stringify(res) });
          })
          .catch(() => {
            batchStats.error++;
            newEntries.push({ id: logIdCounter++, result: "ERROR", json: JSON.stringify({ result: "ERROR", message: "네트워크 오류" }) });
          });
      })
    );

    newEntries.sort((a, b) => a.id - b.id);
    setLogs(prev => [...prev, ...newEntries].slice(-500));
    setStats(prev => ({
      success:   prev.success   + batchStats.success,
      duplicate: prev.duplicate + batchStats.duplicate,
      soldOut:   prev.soldOut   + batchStats.soldOut,
      timeout:   prev.timeout   + batchStats.timeout,
      error:     prev.error     + batchStats.error,
    }));
    setBursting(false);
  };

  const handleReset = async () => {
setResetting(true);
    try {
      await postReset();
      setStats({ success: 0, duplicate: 0, soldOut: 0, timeout: 0, error: 0 });
      setLogs([]);
    } catch (e) {
      if (mountedRef.current) alert("리셋 실패: " + (e instanceof Error ? e.message : "오류"));
    } finally {
      if (mountedRef.current) setResetting(false);
    }
  };

  const pct = status && status.totalCapacity > 0
    ? Math.round((status.reservedCount / status.totalCapacity) * 100)
    : 0;

  return (
    <div className={styles.page}>

      {showHelp && <div className={styles.overlay} onClick={() => setShowHelp(false)} />}

      {showHelp && (
        <div className={styles.modal}>
          <button className={styles.modalClose} onClick={() => setShowHelp(false)}>✕</button>
          <h3 className={styles.modalTitle}>동작 원리</h3>
          <div className={styles.helpBody}>
            <p>콘서트 티켓처럼 재고가 한정된 상품을 동시에 수천 명이 구매하려 할 때, 정해진 수량보다 더 많이 팔리지 않도록 막는 기능입니다.</p>
            <dl className={styles.helpList}>
              <dt>동시성 접속 문제</dt>
              <dd>잔여 티켓이 1장일 때 두 사람이 0.001초 차이로 동시에 구매 버튼을 누르면, 서버가 둘 다 "1장 남음"을 보고 판매를 승인해 버릴 수 있습니다. 이런 동시 처리 문제를 방치하면 티켓이 초과 판매됩니다.</dd>
              <dt>1단계 — 재고 선점</dt>
              <dd>요청이 들어오면 먼저 빠른 임시 저장소(Redis)에서 재고를 차감합니다. 이 과정은 한 번에 한 요청씩만 처리되도록 잠겨 있어, 같은 재고를 두 명이 동시에 가져가는 일이 없습니다. 중복 예매나 재고 부족이면 즉시 반려합니다.</dd>
              <dt>2단계 — DB 저장</dt>
              <dd>재고 선점에 성공한 요청만 실제 데이터베이스에 기록됩니다. 한꺼번에 너무 많은 저장 요청이 몰리지 않도록 동시 처리 수를 제한하며, 0.5초 안에 처리하지 못하면 취소 후 선점한 재고를 반환합니다.</dd>
              <dt>수신 시각(receivedAt)</dt>
              <dd>동시에 버튼을 눌러도 서버에 도착하는 순서는 다릅니다. 먼저 도착한 요청이 티켓을 가져가며, 수신 시각은 그 순서를 확인할 수 있는 기록입니다.</dd>
            </dl>
          </div>
        </div>
      )}

      {/* 헤더 */}
      <header className={styles.header}>
        <div className={styles.titleBlock}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>티켓팅 동시성 제어</h1>
            <button className={styles.helpBtn} onClick={() => setShowHelp(true)}>?</button>
          </div>
          <p className={styles.subtitle}>수천 명이 동시에 눌러도 티켓이 딱 정해진 수만큼만 팔립니다</p>
        </div>
      </header>

      {statusLoaded && !status && (
        <div className={styles.notReady}>
          <span className={styles.notReadyDot} />
          Redis 연결 대기 중
        </div>
      )}

      {/* 이벤트 현황 */}
      {status && (
        <div className={styles.card}>
          <div className={styles.statsCardRow}>
            <div className={styles.statsRow}>
              <div className={styles.stat}>
                <span className={styles.statNum}>{status.totalCapacity}</span>
                <span className={styles.statLabel}>총 티켓</span>
              </div>
              <div className={styles.stat}>
                <span className={`${styles.statNum} ${styles.numReserved}`}>{status.reservedCount}</span>
                <span className={styles.statLabel}>예매 완료</span>
              </div>
              <div className={styles.stat}>
                <span className={`${styles.statNum} ${status.remaining === 0 ? styles.numSoldOut : styles.numRemaining}`}>
                  {status.remaining}
                </span>
                <span className={styles.statLabel}>잔여</span>
              </div>
            </div>
            <LoadingButton className={styles.resetBtn} onClick={handleReset} disabled={reserving || bursting} isLoading={resetting} loadingText="초기화 중">
              초기화
            </LoadingButton>
            <button className={styles.resetIconBtn} onClick={handleReset} disabled={resetting || reserving || bursting} aria-label="초기화">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                <path d="M3 3v5h5" />
              </svg>
            </button>
          </div>
          <div className={styles.progressTrack}>
            <div className={styles.progressFill} style={{ width: `${pct}%` }} />
          </div>
        </div>
      )}

      {/* 액션 */}
      {status && (
        <div className={styles.actionGrid}>
          <div className={styles.card}>
            <h2 className={styles.cardTitle}>개인 예매</h2>
            <div className={styles.row}>
              <label htmlFor="ticketing-user-id" className="sr-only">사용자 ID</label>
              <input
                id="ticketing-user-id"
                className={styles.input}
                type="text"
                placeholder="사용자 ID (예: user_001)"
                value={userId}
                onChange={e => setUserId(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleReserve()}
                disabled={reserving}
              />
              <LoadingButton className={styles.primaryBtn} onClick={handleReserve} disabled={!userId.trim()} isLoading={reserving} loadingText="처리 중">
                예매
              </LoadingButton>
            </div>
          </div>

          <div className={styles.card}>
            <h2 className={styles.cardTitle}>동시 요청</h2>
            <div className={styles.sliderRow}>
              <label htmlFor="ticketing-burst-count" className="sr-only">동시 요청 수</label>
              <input
                id="ticketing-burst-count"
                type="range" min={10} max={200} step={10}
                value={burstCount}
                onChange={e => setBurstCount(Number(e.target.value))}
                className={styles.slider}
                disabled={bursting}
              />
              <span className={styles.sliderVal}>{burstCount}명</span>
            </div>
            <LoadingButton className={styles.burstBtn} onClick={handleBurst} isLoading={bursting} loadingText={`${burstCount}개 요청 중`}>
              {burstCount}명 동시 요청
            </LoadingButton>
          </div>
        </div>
      )}

      {/* 로그 */}
      {status && (
        <div className={styles.logCard}>
          <h2 className={styles.cardTitle}>응답 로그</h2>

          <div className={styles.console} ref={consoleRef}>
            {logs.map(entry => (
              <div key={entry.id} className={`${styles.logLine} ${styles[LOG_CLASS[entry.result]]}`}>
                {entry.json}
              </div>
            ))}
          </div>

          <div className={styles.burstStats}>
            <span className={styles.logOk}>성공 {stats.success}</span>
            <span className={styles.logWarn}>중복 {stats.duplicate}</span>
            <span className={styles.logErr}>매진 {stats.soldOut}</span>
            <span className={styles.logTimeout}>타임아웃 {stats.timeout}</span>
            <span className={styles.logErr}>오류 {stats.error}</span>
          </div>
        </div>
      )}
    </div>
  );
}
