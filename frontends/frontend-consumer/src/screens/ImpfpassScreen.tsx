import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View, ActivityIndicator } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { DiseaseGroup, PatientDossier } from '../types';
import { colors, font, radius, spacing } from '../theme';
import { groupByDisease } from '../utils';
import { Avatar } from '../components/Avatar';
import { VaccinationGroup } from '../components/VaccinationGroup';

type LoadError = {
  title: string;
  message: string;
  retryable: boolean;
};

/** Maps an HTTP status (or a network failure) to a user-facing message. */
function describeError(status: number | null): LoadError {
  if (status === null) {
    return {
      title: 'Verbindungsfehler',
      message: 'Der Server konnte nicht erreicht werden. Bitte prüfen Sie Ihre Verbindung und versuchen Sie es erneut.',
      retryable: true,
    };
  }
  if (status === 404) {
    return {
      title: 'Kein Impfdossier gefunden',
      message: 'Für Ihr Konto ist im System kein Patient registriert. Bitte wenden Sie sich an Ihre Ärztin oder Ihren Arzt.',
      retryable: false,
    };
  }
  if (status >= 500) {
    return {
      title: 'Dienst nicht erreichbar',
      message: 'Das Impfdossier kann momentan nicht geladen werden. Bitte versuchen Sie es später erneut.',
      retryable: true,
    };
  }
  return {
    title: 'Impfdossier konnte nicht geladen werden',
    message: `Unerwartete Antwort vom Server (Status ${status}).`,
    retryable: true,
  };
}

class HttpError extends Error {
  status: number;

  constructor(status: number, detail: unknown) {
    super(`HTTP ${status}: ${typeof detail === 'string' ? detail : JSON.stringify(detail)}`);
    this.status = status;
  }
}

export function ImpfpassScreen() {
  const insets = useSafeAreaInsets();
  const [dossier, setDossier] = useState<PatientDossier | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<LoadError | null>(null);

  const logout = () => {
    try { sessionStorage.removeItem('auth'); } catch {}
    window.location.replace('/');
  };

  const load = useCallback(() => {
    setLoading(true);
    setError(null);

    let patientId = '18c5b3b4-92d6-4481-8906-b7402e5ff685'; // Default Fallback
    try {
      const authStr = sessionStorage.getItem('auth');
      if (authStr) {
        const auth = JSON.parse(authStr);
        if (auth.userId) {
          patientId = auth.userId;
        }
      }
    } catch (e) {
      console.warn('sessionStorage is not available or auth parse failed', e);
    }

    fetch(`/api/bff-consumer/patients/${patientId}`)
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new HttpError(res.status, body?.detail ?? null);
        }
        return res.json();
      })
      .then((data: any) => {
        // Map API response to our client-side type
        const vaccinationsMapped = (data.vaccinations || []).map((v: any, i: number) => ({
          id: v.id || `v-${i}`,
          disease: v.targetDisease || '',
          vaccine: v.vaccineName || '',
          vaccineCode: v.vaccineCode || '',
          date: v.vaccinationDate || '',
          dose: v.doseSequence || '',
          doseNumber: v.doseNumber || undefined,
          seriesDoses: v.seriesDoses || undefined,
          vaccinationReason: v.vaccinationReason || undefined,
          manufacturer: v.manufacturer || '',
          batch: v.lotNumber || '',
          route: v.administrationRoute || '',
          site: v.siteOfAdministration || '',
          season: v.season || undefined,
        }));
        setDossier({
          firstName: data.firstName,
          lastName: data.lastName,
          age: data.age,
          gender: data.gender,
          vaccinations: vaccinationsMapped,
        });
        setLoading(false);
      })
      .catch((err) => {
        console.error('Error loading patient dossier:', err);
        setError(describeError(err instanceof HttpError ? err.status : null));
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const groups = useMemo(() => {
    if (!dossier) return [];
    return groupByDisease(dossier.vaccinations);
  }, [dossier]);

  if (loading) {
    return (
      <View style={styles.root}>
        <StatusBar style="dark" />
        <AppBar topInset={insets.top} onLogout={logout} />
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={[styles.messageText, { marginTop: spacing.md }]}>Lade Impfdossier...</Text>
        </View>
      </View>
    );
  }

  if (error || !dossier) {
    const shown = error ?? describeError(null);
    return (
      <View style={styles.root}>
        <StatusBar style="dark" />
        <AppBar topInset={insets.top} onLogout={logout} />
        <View style={styles.center}>
          <View style={styles.errorCard} accessibilityRole="alert">
            <Text style={styles.emptyTitle}>{shown.title}</Text>
            <Text style={styles.errorMessage}>{shown.message}</Text>
            <View style={styles.errorActions}>
              {shown.retryable && (
                <Pressable style={styles.primaryBtn} onPress={load} accessibilityRole="button">
                  <Text style={styles.primaryBtnText}>Erneut versuchen</Text>
                </Pressable>
              )}
              <Pressable style={styles.secondaryBtn} onPress={logout} accessibilityRole="button">
                <Text style={styles.secondaryBtnText}>Abmelden</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />

      <AppBar topInset={insets.top} onLogout={logout} />

      <FlatList<DiseaseGroup>
        data={groups}
        keyExtractor={(group) => group.disease}
        renderItem={({ item }) => <VaccinationGroup group={item} />}
        ListHeaderComponent={<ProfileSummary dossier={dossier} groupCount={groups.length} />}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>Keine Impfungen erfasst</Text>
            <Text style={styles.emptySub}>Für {dossier.firstName} liegen noch keine Einträge vor.</Text>
          </View>
        }
        contentContainerStyle={[
          styles.listContent,
          { paddingBottom: insets.bottom + spacing.xl },
        ]}
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
}

function AppBar({ topInset, onLogout }: { topInset: number; onLogout: () => void }) {
  return (
    <View style={[styles.appBar, { paddingTop: topInset + spacing.sm }]}>
      <View style={styles.brand}>
        <SwissCross />
        <View>
          <Text accessibilityRole="header" style={styles.brandTitle}>
            Impfdossier <Text style={styles.brandAccent}>CH</Text>
          </Text>
          <Text style={styles.brandSub}>Elektronisches Impfdossier</Text>
        </View>
        <View style={{ flex: 1 }} />
        <Pressable style={styles.logoutBtn} onPress={onLogout} accessibilityLabel="Abmelden" accessibilityRole="button">
          <Text style={styles.logoutText}>Abmelden</Text>
        </Pressable>
      </View>
    </View>
  );
}

function ProfileSummary({
  dossier,
  groupCount,
}: {
  dossier: PatientDossier;
  groupCount: number;
}) {
  const total = dossier.vaccinations.length;
  return (
    <>
      <View
        style={styles.summary}
        accessible
        accessibilityLabel={`${dossier.firstName} ${dossier.lastName}, ${dossier.age} Jahre, ${total} Impfungen erfasst`}>
        <Avatar firstName={dossier.firstName} lastName={dossier.lastName} size={56} />
        <View style={styles.summaryText}>
          <Text style={styles.summaryName}>
            {dossier.lastName}, {dossier.firstName}
          </Text>
          <Text style={styles.summaryMeta}>
            Ich · {dossier.age} Jahre · {dossier.gender}
          </Text>
        </View>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{total}</Text>
          <Text style={styles.summaryStatLabel}>Impfungen</Text>
        </View>
      </View>

      <Text accessibilityRole="header" style={styles.sectionTitle}>
        Impfausweis
      </Text>
      <Text style={styles.sectionSub}>
        {groupCount} {groupCount === 1 ? 'Krankheit' : 'Krankheiten'} abgedeckt
      </Text>
    </>
  );
}

/** Minimal Swiss flag mark built from plain views — no image asset needed. */
function SwissCross() {
  return (
    <View style={styles.flag} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <View style={styles.flagBarV} />
      <View style={styles.flagBarH} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  messageText: {
    fontSize: font.body,
    color: colors.text,
    textAlign: 'center',
  },
  errorCard: {
    width: '100%',
    maxWidth: 480,
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
  },
  errorMessage: {
    marginTop: spacing.sm,
    fontSize: font.label,
    color: colors.textMuted,
    textAlign: 'center',
  },
  errorActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: spacing.sm,
    marginTop: spacing.lg,
  },
  primaryBtn: {
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  primaryBtnText: {
    fontSize: font.label,
    fontWeight: '600',
    color: colors.surface,
  },
  secondaryBtn: {
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  secondaryBtnText: {
    fontSize: font.label,
    fontWeight: '500',
    color: colors.text,
  },
  appBar: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingBottom: spacing.xs,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
  },
  brandTitle: {
    fontSize: font.display,
    fontWeight: '700',
    color: colors.text,
  },
  brandAccent: {
    color: colors.swissRed,
  },
  brandSub: {
    fontSize: font.label,
    color: colors.textMuted,
  },
  flag: {
    width: 32,
    height: 32,
    borderRadius: radius.sm,
    backgroundColor: colors.swissRed,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flagBarV: {
    position: 'absolute',
    width: 6,
    height: 18,
    borderRadius: 1,
    backgroundColor: colors.surface,
  },
  flagBarH: {
    position: 'absolute',
    width: 18,
    height: 6,
    borderRadius: 1,
    backgroundColor: colors.surface,
  },
  listContent: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.xl,
  },
  summaryText: {
    flex: 1,
  },
  summaryName: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
  },
  summaryMeta: {
    marginTop: 2,
    fontSize: font.label,
    color: colors.textMuted,
  },
  summaryStat: {
    alignItems: 'center',
    paddingLeft: spacing.md,
  },
  summaryStatValue: {
    fontSize: font.display,
    fontWeight: '700',
    color: colors.primary,
  },
  summaryStatLabel: {
    fontSize: font.caption,
    color: colors.textMuted,
  },
  sectionTitle: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
  },
  sectionSub: {
    marginTop: 2,
    marginBottom: spacing.md,
    fontSize: font.label,
    color: colors.textMuted,
  },
  logoutBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    marginRight: spacing.lg,
  },
  logoutText: {
    fontSize: font.label,
    fontWeight: '500',
    color: colors.textMuted,
  },
  empty: {
    alignItems: 'center',
    paddingVertical: spacing.xxl,
  },
  emptyTitle: {
    fontSize: font.body,
    fontWeight: '600',
    color: colors.text,
  },
  emptySub: {
    marginTop: spacing.xs,
    fontSize: font.label,
    color: colors.textMuted,
  },
});

