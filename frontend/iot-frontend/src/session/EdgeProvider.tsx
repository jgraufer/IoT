import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { EdgeSummary } from '../types/api';
import { listMyEdges } from './edgesApi';
import { useAuth } from '../auth/AuthContext';
import { localEdge } from '@target';

interface EdgeContextValue {
  edges: EdgeSummary[];
  edgesLoading: boolean;
  activeEdgeId: string | null;
  activeEdgeName: string | null;
  setActiveEdgeId: (id: string) => void;
  refreshEdges: () => Promise<void>;
}

const EdgeContext = createContext<EdgeContextValue | undefined>(undefined);

export function EdgeProvider({ children }: { children: ReactNode }) {
  const { hasCloud, hasEdge } = useAuth();
  const [edges, setEdges] = useState<EdgeSummary[]>([]);
  const [edgesLoading, setEdgesLoading] = useState(false);
  const [selectedEdgeId, setActiveEdgeId] = useState<string | null>(null);
  const [localEdgeId, setLocalEdgeId] = useState<string | null>(null);

  useEffect(() => {
    if (!localEdge || !hasEdge) return;
    let cancelled = false;
    localEdge
      .loadId()
      .then((id) => {
        if (!cancelled) setLocalEdgeId(id);
      })
      .catch(() => {
      });
    return () => {
      cancelled = true;
    };
  }, [hasEdge]);

  const activeEdgeId = localEdge ? localEdgeId : selectedEdgeId;
  const activeEdgeName =
    activeEdgeId === null
      ? null
      : (edges.find((e) => e.edgeId === activeEdgeId)?.name ??
        localEdge?.fallbackName(activeEdgeId) ??
        null);

  const refreshEdges = useCallback(async () => {
    setEdgesLoading(true);
    try {
      const list = await listMyEdges();
      setEdges(list);
      setActiveEdgeId((current) =>
        current && list.some((e) => e.edgeId === current)
          ? current
          : list.length > 0
            ? list[0].edgeId
            : null,
      );
    } catch {
      setEdges([]);
    } finally {
      setEdgesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!hasCloud) {
      setEdges([]);
      setActiveEdgeId(null);
      return;
    }
    refreshEdges();
  }, [hasCloud, refreshEdges]);

  const value = useMemo<EdgeContextValue>(
    () => ({ edges, edgesLoading, activeEdgeId, activeEdgeName, setActiveEdgeId, refreshEdges }),
    [edges, edgesLoading, activeEdgeId, activeEdgeName, refreshEdges],
  );

  return <EdgeContext.Provider value={value}>{children}</EdgeContext.Provider>;
}

export function useEdges(): EdgeContextValue {
  const ctx = useContext(EdgeContext);
  if (!ctx) {
    throw new Error('useEdges debe usarse dentro de <EdgeProvider>');
  }
  return ctx;
}
