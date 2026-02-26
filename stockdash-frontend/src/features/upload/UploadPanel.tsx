import { useState } from 'react';
import { uploadTransactions } from '../../shared/api/portfolioApi';

export function UploadPanel({ onDone }: { onDone: () => Promise<void> | void }) {
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState('');

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      setStatus('Choose a CSV file first.');
      return;
    }

    try {
      const result = await uploadTransactions(file);
      setStatus(`Imported ${result.importedCount}, skipped ${result.skippedCount}.`);
      await onDone();
    } catch (error) {
      setStatus((error as Error).message);
    }
  };

  return (
    <form onSubmit={onSubmit} className="stack gap-sm">
      <label className="field">
        <span>Upload Transactions CSV</span>
        <input type="file" accept=".csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </label>
      <button type="submit">Upload</button>
      {status ? <p className="muted">{status}</p> : null}
    </form>
  );
}
