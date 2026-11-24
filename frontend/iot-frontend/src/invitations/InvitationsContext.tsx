import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { Invitation } from '../types/api';
import { acceptInvitation, listMyInvitations, rejectInvitation } from './invitationsApi';
import { useAuth } from '../auth/AuthContext';
import { useEdges } from '../session/EdgeProvider';

interface InvitationsContextValue {
  invitations: Invitation[];
  count: number;
  accept: (invitationId: string) => Promise<void>;
  reject: (invitationId: string) => Promise<void>;
}

const InvitationsContext = createContext<InvitationsContextValue | undefined>(undefined);

const POLL_MS = 60_000;

export function InvitationsProvider({ children }: { children: ReactNode }) {
  const { hasCloud } = useAuth();
  const { refreshEdges } = useEdges();
  const [invitations, setInvitations] = useState<Invitation[]>([]);

  const refresh = useCallback(async () => {
    try {
      setInvitations(await listMyInvitations());
    } catch {
    }
  }, []);

  useEffect(() => {
    if (!hasCloud) {
      setInvitations([]);
      return;
    }
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [hasCloud, refresh]);

  const accept = useCallback(
    async (invitationId: string) => {
      await acceptInvitation(invitationId);
      await Promise.all([refresh(), refreshEdges()]);
    },
    [refresh, refreshEdges],
  );

  const reject = useCallback(
    async (invitationId: string) => {
      await rejectInvitation(invitationId);
      await refresh();
    },
    [refresh],
  );

  const value = useMemo<InvitationsContextValue>(
    () => ({ invitations, count: invitations.length, accept, reject }),
    [invitations, accept, reject],
  );

  return <InvitationsContext.Provider value={value}>{children}</InvitationsContext.Provider>;
}

export function useInvitations(): InvitationsContextValue {
  const ctx = useContext(InvitationsContext);
  if (!ctx) {
    throw new Error('useInvitations debe usarse dentro de <InvitationsProvider>');
  }
  return ctx;
}
