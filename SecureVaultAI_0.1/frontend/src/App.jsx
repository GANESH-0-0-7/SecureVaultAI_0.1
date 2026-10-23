import { useState } from 'react';
import AuthForm from './components/AuthForm';
import Vault from './components/Vault';

function App() {
  const [session, setSession] = useState(null);
  const [privateKey, setPrivateKey] = useState(null);
  const [keyBackup, setKeyBackup] = useState(null);

  function authenticate(authenticatedSession, unlockedPrivateKey, backup) {
    setSession(authenticatedSession);
    setPrivateKey(unlockedPrivateKey);
    setKeyBackup(backup);
  }

  function signOut() {
    setSession(null);
    setPrivateKey(null);
    setKeyBackup(null);
  }

  if (!session || !privateKey || !keyBackup) {
    return <AuthForm onAuthenticated={authenticate} />;
  }

  return (
    <Vault
      session={session}
      privateKey={privateKey}
      keyBackup={keyBackup}
      onSignOut={signOut}
    />
  );
}

export default App;
