import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View, ActivityIndicator, useWindowDimensions } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import type { DiseaseGroup, PatientDossier } from '../types';
import { WIDE_LAYOUT_MIN_WIDTH, colors, font, radius, spacing } from '../theme';
import { groupByDisease } from '../utils';
import { Avatar } from '../components/Avatar';
import { VaccinationGroup } from '../components/VaccinationGroup';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import i18n from '../i18n';

type LoadError = {
  key: 'network' | 'notFound' | 'unavailable' | 'unexpected';
  status: number | null;
  retryable: boolean;
};

/** Maps an HTTP status (or a network failure) to a translatable error. */
function describeError(status: number | null): LoadError {
  if (status === null) return { key: 'network', status, retryable: true };
  if (status === 404) return { key: 'notFound', status, retryable: false };
  if (status >= 500) return { key: 'unavailable', status, retryable: true };
  return { key: 'unexpected', status, retryable: true };
}

function genderLabel(gender: string): string {
  return i18n.exists(`gender.${gender}`) ? i18n.t(`gender.${gender}`) : gender;
}

class HttpError extends Error {
  status: number;

  constructor(status: number, detail: unknown) {
    super(`HTTP ${status}: ${typeof detail === 'string' ? detail : JSON.stringify(detail)}`);
    this.status = status;
  }
}

export function ImpfpassScreen() {
  const { t } = useTranslation();
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
          <Text style={[styles.messageText, { marginTop: spacing.md }]}>{t('dossier.loading')}</Text>
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
            <Text style={styles.emptyTitle}>{t(`error.${shown.key}.title`)}</Text>
            <Text style={styles.errorMessage}>{t(`error.${shown.key}.message`, { status: shown.status })}</Text>
            <View style={styles.errorActions}>
              {shown.retryable && (
                <Pressable style={styles.primaryBtn} onPress={load} accessibilityRole="button">
                  <Text style={styles.primaryBtnText}>{t('common.retry')}</Text>
                </Pressable>
              )}
              <Pressable style={styles.secondaryBtn} onPress={logout} accessibilityRole="button">
                <Text style={styles.secondaryBtnText}>{t('common.logout')}</Text>
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
            <Text style={styles.emptyTitle}>{t('dossier.emptyTitle')}</Text>
            <Text style={styles.emptySub}>{t('dossier.emptyText', { name: dossier.firstName })}</Text>
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
  const { t } = useTranslation();
  const compact = useWindowDimensions().width < WIDE_LAYOUT_MIN_WIDTH;
  return (
    <View style={[styles.appBar, { paddingTop: topInset + spacing.sm }]}>
      <View style={[styles.brand, compact && styles.brandCompact]}>
        <SwissCross />
        <View style={styles.brandText}>
          <Text accessibilityRole="header" style={[styles.brandTitle, compact && styles.brandTitleCompact]} numberOfLines={1}>
            Impfdossier <Text style={styles.brandAccent}>CH</Text>
          </Text>
          <Text style={styles.brandSub} numberOfLines={1}>{t('appBar.subtitle')}</Text>
        </View>
        <LanguageSwitcher compact={compact} />
        <Pressable
          style={({ pressed }) => [styles.logoutBtn, compact && styles.logoutIconBtn, pressed && styles.pressed]}
          onPress={onLogout}
          accessibilityLabel={t('common.logout')}
          accessibilityRole="button">
          <LogoutIcon />
          {!compact && <Text style={styles.logoutText} numberOfLines={1}>{t('common.logout')}</Text>}
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
  const { t } = useTranslation();
  const total = dossier.vaccinations.length;
  const age = t('common.years', { count: dossier.age });
  return (
    <>
      <View
        style={styles.summary}
        accessible
        accessibilityLabel={t('dossier.summaryLabel', {
          name: `${dossier.firstName} ${dossier.lastName}`,
          age,
          vaccinations: t('dossier.vaccinationsRecorded', { count: total }),
        })}>
        <Avatar firstName={dossier.firstName} lastName={dossier.lastName} size={56} />
        <View style={styles.summaryText}>
          <Text style={styles.summaryName}>
            {dossier.lastName}, {dossier.firstName}
          </Text>
          <Text style={styles.summaryMeta}>
            {t('dossier.self')} · {age} · {genderLabel(dossier.gender)}
          </Text>
        </View>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{total}</Text>
          <Text style={styles.summaryStatLabel}>{t('dossier.vaccinations', { count: total })}</Text>
        </View>
      </View>

      <Text accessibilityRole="header" style={styles.sectionTitle}>
        {t('dossier.record')}
      </Text>
      <Text style={styles.sectionSub}>
        {t('dossier.diseasesCovered', { count: groupCount })}
      </Text>
    </>
  );
}

/** Door-with-arrow sign-out mark built from plain views — no icon library needed. */
function LogoutIcon() {
  return (
    <View style={styles.logoutIcon} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <View style={styles.logoutIconDoor} />
      <View style={styles.logoutIconShaft} />
      <View style={styles.logoutIconHead} />
    </View>
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
    gap: spacing.sm,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
  },
  brandCompact: {
    paddingHorizontal: spacing.md,
  },
  brandText: {
    flex: 1,
    minWidth: 0,
  },
  brandTitle: {
    fontSize: font.display,
    fontWeight: '700',
    color: colors.text,
  },
  brandTitleCompact: {
    fontSize: font.title,
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
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    minHeight: 44,
    paddingHorizontal: spacing.sm,
    flexShrink: 0,
  },
  logoutIconBtn: {
    width: 44,
    justifyContent: 'center',
    paddingHorizontal: 0,
  },
  logoutIcon: {
    width: 20,
    height: 20,
  },
  logoutIconDoor: {
    position: 'absolute',
    left: 1,
    top: 2,
    width: 10,
    height: 16,
    borderWidth: 2,
    borderRightWidth: 0,
    borderColor: colors.textMuted,
    borderTopLeftRadius: 2,
    borderBottomLeftRadius: 2,
  },
  logoutIconShaft: {
    position: 'absolute',
    left: 7,
    top: 9,
    width: 11,
    height: 2,
    backgroundColor: colors.textMuted,
  },
  logoutIconHead: {
    position: 'absolute',
    left: 10,
    top: 6,
    width: 8,
    height: 8,
    borderTopWidth: 2,
    borderRightWidth: 2,
    borderColor: colors.textMuted,
    transform: [{ rotate: '45deg' }],
  },
  pressed: {
    opacity: 0.7,
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

