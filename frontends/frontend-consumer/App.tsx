import { SafeAreaProvider } from 'react-native-safe-area-context';

import './src/i18n';

import { ImpfpassScreen } from './src/screens/ImpfpassScreen';

export default function App() {
  return (
    <SafeAreaProvider style={{ flex: 1 }}>
      <ImpfpassScreen />
    </SafeAreaProvider>
  );
}
