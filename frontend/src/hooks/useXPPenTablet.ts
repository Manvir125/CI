import { useEffect, useRef, useState, useCallback } from 'react';

export interface XPPenDevice {
    connected: boolean;
    product?: string;
    vendor?: string;
    maxX: number;
    maxY: number;
    maxPressure: number;
}

export interface PenEvent {
    x: number;
    y: number;
    pressure: number;
    status: 'Hover' | 'Down' | 'Move' | 'Up' | 'Leave';
    maxX: number;
    maxY: number;
    maxPressure: number;
}

interface WsMessage {
    type: string;
    status?: string;
    x?: number;
    y?: number;
    pressure?: number;
    maxX?: number;
    maxY?: number;
    maxPressure?: number;
    product?: string;
    vendor?: string;
}

interface UseXPPenTabletOptions {
    url?: string;
    onPenEvent?: (event: PenEvent) => void;
    enabled?: boolean;
}

/**
 * React hook to connect to the XP Pen WebSocket bridge service.
 * Returns device status and a stream of pen events.
 */
export function useXPPenTablet({
    url = 'ws://localhost:5100',
    onPenEvent,
    enabled = true,
}: UseXPPenTabletOptions = {}) {
    const [device, setDevice] = useState<XPPenDevice>({
        connected: false,
        maxX: 0,
        maxY: 0,
        maxPressure: 0,
    });
    const [wsState, setWsState] = useState<'connecting' | 'open' | 'closed'>('closed');
    const wsRef = useRef<WebSocket | null>(null);
    const onPenEventRef = useRef(onPenEvent);
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    // Keep callback ref current without re-triggering effect
    useEffect(() => {
        onPenEventRef.current = onPenEvent;
    }, [onPenEvent]);

    const connect = useCallback(() => {
        if (!enabled) return;

        try {
            const ws = new WebSocket(url);
            wsRef.current = ws;
            setWsState('connecting');

            ws.onopen = () => {
                setWsState('open');
                console.log('[XPPen] WebSocket connected');
            };

            ws.onmessage = (evt) => {
                try {
                    const msg: WsMessage = JSON.parse(evt.data);

                    if (msg.type === 'device') {
                        setDevice({
                            connected: msg.status === 'connected',
                            product: msg.product,
                            vendor: msg.vendor,
                            maxX: msg.maxX ?? 0,
                            maxY: msg.maxY ?? 0,
                            maxPressure: msg.maxPressure ?? 0,
                        });
                    } else if (msg.type === 'pen') {
                        onPenEventRef.current?.({
                            x: msg.x!,
                            y: msg.y!,
                            pressure: msg.pressure!,
                            status: msg.status as PenEvent['status'],
                            maxX: msg.maxX!,
                            maxY: msg.maxY!,
                            maxPressure: msg.maxPressure!,
                        });
                    }
                } catch { /* ignore parse errors */ }
            };

            ws.onclose = () => {
                setWsState('closed');
                setDevice(d => ({ ...d, connected: false }));
                console.log('[XPPen] WebSocket closed, reconnecting in 3s…');
                reconnectTimerRef.current = setTimeout(connect, 3000);
            };

            ws.onerror = () => {
                ws.close();
            };
        } catch {
            setWsState('closed');
            reconnectTimerRef.current = setTimeout(connect, 3000);
        }
    }, [url, enabled]);

    useEffect(() => {
        connect();
        return () => {
            clearTimeout(reconnectTimerRef.current);
            wsRef.current?.close();
        };
    }, [connect]);

    return { device, wsState };
}
