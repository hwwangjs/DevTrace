import { useEffect, useMemo, useRef, useState } from 'react';

import { formatLogParseSummary } from './analysisSummary.js';

const languages = ['Java', 'JavaScript', 'TypeScript', 'Python', 'SQL', 'Other'];
const HISTORY_STATE_KEY = 'devTraceView';
const ANALYSIS_ID_QUERY_KEY = 'analysisId';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

function apiUrl(path) {
  return `${API_BASE_URL}${path}`;
}

function workspaceHistoryState() {
  return { [HISTORY_STATE_KEY]: { view: 'workspace' } };
}

function detailHistoryState(id, fromWorkspace = false) {
  return { [HISTORY_STATE_KEY]: { view: 'detail', id, fromWorkspace } };
}

function analysisIdFromUrl() {
  const value = new URLSearchParams(window.location.search).get(ANALYSIS_ID_QUERY_KEY);
  if (!value || !/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}

function statusLabel(status) {
  if (status === 'PENDING') return '대기 중';
  if (status === 'COMPLETED') return '완료';
  if (status === 'FAILED') return '실패';
  return status || '상태 없음';
}

function AuthScreen({ mode, onModeChange, onSubmit, busy, error, notice }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [nickname, setNickname] = useState('');
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    setPassword('');
    setPasswordConfirmation('');
    setValidationError('');
  }, [mode]);

  function submit(event) {
    event.preventDefault();
    setValidationError('');
    if (mode === 'signup' && password !== passwordConfirmation) {
      setValidationError('비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    onSubmit({ email, password, nickname });
  }

  return (
    <main className="auth-shell">
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-brand"><span className="brand-mark">D</span><span>DevTrace</span></div>
        <p className="eyebrow">SECURE WORKSPACE</p>
        <h1 id="auth-title">{mode === 'signup' ? '계정 만들기' : '로그인'}</h1>
        <p className="auth-description">{mode === 'signup' ? '회원가입 후 개인 분석 워크스페이스를 사용할 수 있습니다.' : '로그인하면 내 분석 워크스페이스를 확인할 수 있습니다.'}</p>
        {error && <div className="auth-message error" role="alert">{error}</div>}
        {validationError && <div className="auth-message error" role="alert">{validationError}</div>}
        {notice && <div className="auth-message" role="status">{notice}</div>}
        <form className="auth-form" onSubmit={submit}>
          <label className="form-field">이메일
            <input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={320} required />
          </label>
          {mode === 'signup' && <label className="form-field">닉네임
            <input type="text" autoComplete="nickname" value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={80} required />
          </label>}
          <label className="form-field">비밀번호
            <input type="password" autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} value={password} onChange={(event) => setPassword(event.target.value)} minLength={mode === 'signup' ? 12 : undefined} maxLength={72} required />
          </label>
          {mode === 'signup' && <label className="form-field">비밀번호 확인
            <input type="password" autoComplete="new-password" value={passwordConfirmation} onChange={(event) => setPasswordConfirmation(event.target.value)} maxLength={72} required />
          </label>}
          <button className="button button-primary auth-submit" type="submit" disabled={busy}>
            {busy ? '처리 중…' : mode === 'signup' ? '회원가입' : '로그인'}
          </button>
        </form>
        <p className="auth-switch">
          {mode === 'signup' ? '이미 계정이 있나요?' : '처음 방문하셨나요?'}{' '}
          <button type="button" onClick={() => onModeChange(mode === 'signup' ? 'login' : 'signup')}>
            {mode === 'signup' ? '로그인' : '회원가입'}
          </button>
        </p>
      </section>
    </main>
  );
}

export default function App() {
  const [authStatus, setAuthStatus] = useState('checking');
  const [authMode, setAuthMode] = useState('login');
  const [authUser, setAuthUser] = useState(null);
  const [authError, setAuthError] = useState('');
  const [authNotice, setAuthNotice] = useState('');
  const [authBusy, setAuthBusy] = useState(false);
  const [authServiceError, setAuthServiceError] = useState('');
  const [sessionNotice, setSessionNotice] = useState('');
  const csrfRef = useRef(null);
  const unauthenticatedRef = useRef(false);
  const [helloMessage, setHelloMessage] = useState('');
  const [helloError, setHelloError] = useState('');
  const [helloLoading, setHelloLoading] = useState(false);
  const [language, setLanguage] = useState('Java');
  const [errorMessage, setErrorMessage] = useState('');
  const [analysisResult, setAnalysisResult] = useState(null);
  const [analysisError, setAnalysisError] = useState('');
  const [analysisStatus, setAnalysisStatus] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [analyses, setAnalyses] = useState([]);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedId, setSelectedId] = useState(() => analysisIdFromUrl());
  const [detailAnalysis, setDetailAnalysis] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailReloadKey, setDetailReloadKey] = useState(0);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);
  const workspaceNavigationPending = useRef(false);
  const [resolutionNote, setResolutionNote] = useState(null);
  const [notesLoading, setNotesLoading] = useState(false);
  const [notesSaving, setNotesSaving] = useState(false);
  const [notesLoadError, setNotesLoadError] = useState('');
  const [notesSaveMessage, setNotesSaveMessage] = useState('');
  const [notesSaveError, setNotesSaveError] = useState('');
  const [copyStatus, setCopyStatus] = useState('');
  const [parseCopyStatus, setParseCopyStatus] = useState('');
  const [logTextsById, setLogTextsById] = useState({});
  const [parseResultsById, setParseResultsById] = useState({});
  const [parseErrorsById, setParseErrorsById] = useState({});
  const [parseLoadingById, setParseLoadingById] = useState({});
  const [aiUseSelected, setAiUseSelected] = useState(false);
  const [openAiApiKey, setOpenAiApiKey] = useState('');
  const [aiResultsById, setAiResultsById] = useState({});
  const [aiErrorsById, setAiErrorsById] = useState({});
  const [aiLoadingById, setAiLoadingById] = useState({});

  function clearPrivateUiState() {
    setAnalyses([]);
    setListError('');
    setSearchTerm('');
    setSelectedId(null);
    setDetailAnalysis(null);
    setDetailLoading(false);
    setDetailError('');
    setDeleteConfirmationOpen(false);
    setDeleteError('');
    setDeleting(false);
    setResolutionNote(null);
    setNotesLoading(false);
    setNotesSaving(false);
    setNotesLoadError('');
    setNotesSaveMessage('');
    setNotesSaveError('');
    setCopyStatus('');
    setParseCopyStatus('');
    setLogTextsById({});
    setParseResultsById({});
    setParseErrorsById({});
    setParseLoadingById({});
    setAiUseSelected(false);
    setOpenAiApiKey('');
    setAiResultsById({});
    setAiErrorsById({});
    setAiLoadingById({});
    setAnalysisResult(null);
    setAnalysisError('');
    setAnalysisStatus('');
    setSubmitting(false);
    setListLoading(false);
    setHelloLoading(false);
    setLanguage('Java');
    setErrorMessage('');
    setHelloMessage('');
    setHelloError('');
    csrfRef.current = null;
    window.history.replaceState(workspaceHistoryState(), '', window.location.href);
  }

  function expireSession() {
    if (unauthenticatedRef.current) return;
    unauthenticatedRef.current = true;
    clearPrivateUiState();
    setAuthUser(null);
    setAuthMode('login');
    setAuthError('세션이 만료되었습니다. 다시 로그인해 주세요.');
    setAuthNotice('');
    setSessionNotice('');
    setAuthStatus('unauthenticated');
  }

  async function refreshCsrf(signal) {
    const response = await fetch(apiUrl('/api/auth/csrf'), { credentials: 'include', signal });
    if (!response.ok) throw new Error(`보안 토큰을 가져오지 못했습니다. (HTTP ${response.status})`);
    const token = await response.json();
    if (!token?.headerName || !token?.token) throw new Error('서버의 보안 토큰 응답을 확인할 수 없습니다.');
    csrfRef.current = token;
    return token;
  }

  async function apiFetch(path, options = {}, authenticationRequired = true) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const csrf = csrfRef.current || await refreshCsrf();
      headers.set(csrf.headerName, csrf.token);
    }
    const response = await fetch(apiUrl(path), {
      ...options,
      method,
      headers,
      credentials: 'include',
    });
    if (response.status === 401 && authenticationRequired) {
      expireSession();
      const error = new Error('세션이 만료되었습니다. 다시 로그인해 주세요.');
      error.sessionExpired = true;
      throw error;
    }
    if (response.status === 403) {
      csrfRef.current = null;
      refreshCsrf().catch(() => {});
      throw new Error('요청 보안 검증에 실패했습니다. 토큰을 새로 받은 뒤 다시 시도해 주세요.');
    }
    return response;
  }

  async function loadAnalyses(options = {}) {
    setListLoading(true);
    setListError('');
    try {
      const response = await apiFetch('/api/analyses', { signal: options.signal });
      if (!response.ok) throw new Error(`분석 목록을 불러오지 못했습니다. (HTTP ${response.status})`);
      const data = await response.json();
      if (!Array.isArray(data)) throw new Error('백엔드 응답 형식이 올바르지 않습니다.');
      const sorted = [...data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setAnalyses(sorted);
    } catch (requestError) {
      if (requestError.name !== 'AbortError' && !requestError.sessionExpired) {
        setListError(requestError.message || '분석 목록을 불러오는 중 오류가 발생했습니다.');
      }
    } finally {
      if (!options.signal?.aborted) setListLoading(false);
    }
  }

  useEffect(() => {
    const initialId = analysisIdFromUrl();
    const initialState = initialId === null ? workspaceHistoryState() : detailHistoryState(initialId);
    window.history.replaceState(initialState, '', `${window.location.pathname}${window.location.search}${window.location.hash}`);
    function restoreView(event) {
      workspaceNavigationPending.current = false;
      const urlId = analysisIdFromUrl();
      const view = event.state?.[HISTORY_STATE_KEY];
      const historyId = view?.view === 'detail' && Number.isSafeInteger(view.id) && view.id > 0 ? view.id : null;
      setSelectedId(urlId ?? historyId);
      setCopyStatus('');
      setParseCopyStatus('');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    window.addEventListener('popstate', restoreView);
    const controller = new AbortController();
    let active = true;
    async function initializeSession() {
      setAuthStatus('checking');
      setAuthServiceError('');
      try {
        const response = await fetch(apiUrl('/api/auth/me'), { credentials: 'include', signal: controller.signal });
        if (response.status === 401) {
          await refreshCsrf(controller.signal);
          if (!active) return;
          setAuthStatus('unauthenticated');
          return;
        }
        if (!response.ok) throw new Error(`로그인 상태를 확인하지 못했습니다. (HTTP ${response.status})`);
        const profile = await response.json();
        await refreshCsrf(controller.signal);
        if (!active) return;
        unauthenticatedRef.current = false;
        setAuthUser(profile);
        setAuthStatus('authenticated');
        await loadAnalyses({ signal: controller.signal });
      } catch (requestError) {
        if (!active || requestError.name === 'AbortError') return;
        setAuthServiceError(requestError.message || '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
        setAuthStatus('error');
      }
    }
    initializeSession();
    return () => {
      active = false;
      controller.abort();
      window.removeEventListener('popstate', restoreView);
    };
  }, []);

  useEffect(() => {
    if (selectedId === null || authStatus !== 'authenticated') {
      setDetailAnalysis(null);
      setDetailLoading(false);
      setDetailError('');
      return undefined;
    }

    const controller = new AbortController();
    setDetailAnalysis(null);
    setDetailLoading(true);
    setDetailError('');
    apiFetch(`/api/analyses/${selectedId}`, { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 404) throw new Error('분석 요청을 찾을 수 없습니다. 목록을 새로 고쳐 주세요.');
        if (!response.ok) throw new Error(`분석 정보를 불러오지 못했습니다. (HTTP ${response.status})`);
        const item = await response.json();
        if (item?.id !== selectedId) throw new Error('서버 응답의 분석 ID가 일치하지 않습니다.');
        setDetailAnalysis(item);
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError' && !requestError.sessionExpired) {
          setDetailError(requestError.message || '분석 정보를 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, authStatus, detailReloadKey]);

  useEffect(() => {
    if (selectedId === null || authStatus !== 'authenticated') {
      setResolutionNote(null);
      setNotesLoading(false);
      setNotesLoadError('');
      setNotesSaveMessage('');
      setNotesSaveError('');
      return undefined;
    }

    const controller = new AbortController();
    setResolutionNote(null);
    setNotesLoading(true);
    setNotesLoadError('');
    setNotesSaveMessage('');
    setNotesSaveError('');
    apiFetch(`/api/analyses/${selectedId}/notes`, { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 404) throw new Error('해결 기록을 불러올 분석 요청을 찾을 수 없습니다.');
        if (!response.ok) throw new Error(`해결 기록을 불러오지 못했습니다. (HTTP ${response.status})`);
        const note = await response.json();
        if (note?.analysisRequestId !== selectedId) throw new Error('서버 응답의 분석 ID가 일치하지 않습니다.');
        setResolutionNote(note);
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError' && !requestError.sessionExpired) {
          setNotesLoadError(requestError.message || '해결 기록을 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setNotesLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, authStatus, detailReloadKey]);

  async function submitAuth({ email, password, nickname }) {
    setAuthError('');
    setAuthNotice('');
    setAuthBusy(true);
    try {
      const csrf = csrfRef.current || await refreshCsrf();
      const signup = authMode === 'signup';
      const response = await fetch(apiUrl(`/api/auth/${signup ? 'signup' : 'login'}`), {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify(signup ? { email, password, nickname } : { email, password }),
      });
      if (response.status === 403) {
        csrfRef.current = null;
        await refreshCsrf();
        setAuthError('보안 토큰이 만료되었습니다. 다시 시도해 주세요.');
        return;
      }
      let body = {};
      try { body = await response.json(); } catch { /* Keep a generic message for non-JSON errors. */ }
      if (!response.ok) {
        const message = response.status === 401
          ? '이메일 또는 비밀번호를 확인해 주세요.'
          : body.message || `요청을 처리하지 못했습니다. (HTTP ${response.status})`;
        throw new Error(message);
      }
      if (signup) {
        setAuthMode('login');
        setAuthNotice('회원가입이 완료되었습니다. 로그인해 주세요.');
        return;
      }
      csrfRef.current = null;
      await refreshCsrf();
      unauthenticatedRef.current = false;
      setAuthUser(body);
      setAuthStatus('authenticated');
      setAuthError('');
      setAuthNotice('');
      setSessionNotice('');
      await loadAnalyses();
    } catch (requestError) {
      setAuthError(requestError.message || '인증 요청에 실패했습니다.');
    } finally {
      setAuthBusy(false);
    }
  }

  async function logout() {
    setSessionNotice('');
    try {
      const response = await apiFetch('/api/auth/logout', { method: 'POST' });
      if (!response.ok && response.status !== 204) {
        throw new Error(`로그아웃하지 못했습니다. (HTTP ${response.status})`);
      }
      clearPrivateUiState();
      unauthenticatedRef.current = false;
      setAuthUser(null);
      setAuthMode('login');
      setAuthError('');
      setAuthNotice('로그아웃했습니다.');
      setAuthStatus('unauthenticated');
      await refreshCsrf();
    } catch (requestError) {
      if (!unauthenticatedRef.current) {
        setSessionNotice(requestError.message || '로그아웃 요청에 실패했습니다.');
      }
    }
  }

  const filteredAnalyses = useMemo(() => {
    const query = searchTerm.trim().toLocaleLowerCase();
    if (!query) return analyses;
    return analyses.filter((item) =>
      `${item.language ?? ''} ${item.errorMessage ?? ''}`.toLocaleLowerCase().includes(query),
    );
  }, [analyses, searchTerm]);

  const pendingCount = analyses.filter((item) => item.analysisStatus === 'PENDING').length;
  const completedCount = analyses.filter((item) => item.analysisStatus === 'COMPLETED').length;
  const selectedAnalysis = detailAnalysis;

  function openAnalysis(item) {
    if (selectedId === item.id) return;
    workspaceNavigationPending.current = false;
    const nextUrl = new URL(window.location.href);
    nextUrl.searchParams.set(ANALYSIS_ID_QUERY_KEY, String(item.id));
    window.history.pushState(detailHistoryState(item.id, true), '', `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`);
    setSelectedId(item.id);
    setDeleteConfirmationOpen(false);
    setDeleteError('');
    setCopyStatus('');
    setParseCopyStatus('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function returnToList() {
    setCopyStatus('');
    setParseCopyStatus('');
    const view = window.history.state?.[HISTORY_STATE_KEY];
    if (selectedId !== null && view?.view === 'detail' && view.id === selectedId && view.fromWorkspace) {
      if (!workspaceNavigationPending.current) {
        workspaceNavigationPending.current = true;
        window.history.back();
      }
      return;
    }
    const nextUrl = new URL(window.location.href);
    nextUrl.searchParams.delete(ANALYSIS_ID_QUERY_KEY);
    window.history.replaceState(workspaceHistoryState(), '', `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`);
    setSelectedId(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function deleteSelectedAnalysis() {
    if (selectedId === null || deleting) return;
    setDeleting(true);
    setDeleteError('');
    try {
      const response = await apiFetch(`/api/analyses/${selectedId}`, { method: 'DELETE' });
      if (response.status === 404) throw new Error('분석 요청을 찾을 수 없거나 삭제 권한이 없습니다.');
      if (!response.ok) throw new Error(`분석 요청을 삭제하지 못했습니다. (HTTP ${response.status})`);

      const deletedId = selectedId;
      setAnalyses((current) => current.filter((item) => item.id !== deletedId));
      setDetailAnalysis(null);
      setDeleteConfirmationOpen(false);
      setResolutionNote(null);
      setLogTextsById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setParseResultsById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setParseErrorsById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setParseLoadingById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setAiResultsById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setAiErrorsById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      setAiLoadingById((current) => { const next = { ...current }; delete next[deletedId]; return next; });
      await loadAnalyses();
      returnToList();
    } catch (requestError) {
      if (!requestError.sessionExpired) setDeleteError(requestError.message || '분석 요청을 삭제하지 못했습니다.');
    } finally {
      setDeleting(false);
    }
  }

  async function copyErrorMessage() {
    try {
      await navigator.clipboard.writeText(selectedAnalysis.errorMessage || '');
      setCopyStatus('오류 메시지를 복사했습니다.');
    } catch {
      setCopyStatus('복사하지 못했습니다. 브라우저 권한을 확인해 주세요.');
    }
  }

  async function copyParsedSummary() {
    const summary = formatLogParseSummary(parseResultsById[selectedId]);
    if (!summary) {
      setParseCopyStatus('복사할 추출 정보가 없습니다.');
      return;
    }
    try {
      await navigator.clipboard.writeText(summary);
      setParseCopyStatus('분석 결과를 복사했습니다.');
    } catch {
      setParseCopyStatus('복사하지 못했습니다. 브라우저 권한을 확인해 주세요.');
    }
  }

  function updateResolutionNote(field, value) {
    setResolutionNote((current) => current ? { ...current, [field]: value } : current);
    setNotesSaveMessage('');
    setNotesSaveError('');
  }

  async function saveResolutionNote() {
    if (selectedId === null || !resolutionNote || notesSaving || notesLoading || notesLoadError) return;
    const analysisId = selectedId;
    setNotesSaving(true);
    setNotesSaveMessage('');
    setNotesSaveError('');
    try {
      const response = await apiFetch(`/api/analyses/${analysisId}/notes`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          rootCause: resolutionNote.rootCause,
          attemptedSteps: resolutionNote.attemptedSteps,
          finalSolution: resolutionNote.finalSolution,
          testResult: resolutionNote.testResult,
          resolutionStatus: resolutionNote.resolutionStatus,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (response.status === 404) throw new Error('분석 요청을 찾을 수 없거나 기록을 저장할 권한이 없습니다.');
      if (!response.ok) {
        const details = body.errors?.map((item) => `${item.field}: ${item.reason}`).join('\n');
        throw new Error(details || body.message || `해결 기록을 저장하지 못했습니다. (HTTP ${response.status})`);
      }
      if (selectedId === analysisId) {
        setResolutionNote(body);
        setNotesSaveMessage('해결 기록을 저장했습니다.');
      }
    } catch (requestError) {
      if (selectedId === analysisId && !requestError.sessionExpired) {
        setNotesSaveError(requestError.message || '해결 기록을 저장하지 못했습니다.');
      }
    } finally {
      setNotesSaving(false);
    }
  }

  async function parseLog() {
    const logText = logTextsById[selectedId] || '';
    setParseErrorsById((current) => ({ ...current, [selectedId]: '' }));
    if (!logText.trim()) {
      setParseErrorsById((current) => ({ ...current, [selectedId]: '로그를 입력해 주세요.' }));
      return;
    }
    if (logText.length > 50_000) {
      setParseErrorsById((current) => ({ ...current, [selectedId]: '로그는 50,000자 이하여야 합니다.' }));
      return;
    }

    setParseResultsById((current) => ({ ...current, [selectedId]: null }));
    setParseCopyStatus('');
    setParseLoadingById((current) => ({ ...current, [selectedId]: true }));
    try {
      const response = await apiFetch('/api/analyses/parse-log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ logText }),
      });
      const body = await response.json();
      if (!response.ok) {
        const details = body.errors?.map((item) => `${item.field}: ${item.reason}`).join('\n');
        throw new Error(details || body.message || `로그를 처리하지 못했습니다. (HTTP ${response.status})`);
      }
      setParseResultsById((current) => ({ ...current, [selectedId]: body }));
    } catch (requestError) {
      if (!requestError.sessionExpired) setParseErrorsById((current) => ({ ...current, [selectedId]: requestError.message || '로그를 처리하지 못했습니다.' }));
    } finally {
      if (!unauthenticatedRef.current) setParseLoadingById((current) => ({ ...current, [selectedId]: false }));
    }
  }

  async function analyzeWithAi() {
    const logText = logTextsById[selectedId] || '';
    if (!aiUseSelected) {
      setAiErrorsById((current) => ({ ...current, [selectedId]: 'AI 분석 사용에 동의해 주세요.' }));
      return;
    }
    if (!logText.trim() || logText.length > 50_000) {
      setAiErrorsById((current) => ({ ...current, [selectedId]: '로그를 입력해 주세요. (최대 50,000자)' }));
      return;
    }

    setAiErrorsById((current) => ({ ...current, [selectedId]: '' }));
    setAiResultsById((current) => ({ ...current, [selectedId]: null }));
    setAiLoadingById((current) => ({ ...current, [selectedId]: true }));
    const apiKey = openAiApiKey;
    try {
      const response = await apiFetch('/api/analyses/ai', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ consent: true, analysisId: selectedAnalysis.id, apiKey, language: selectedAnalysis.language, logText }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || `AI 분석에 실패했습니다. (HTTP ${response.status})`);
      setAiResultsById((current) => ({ ...current, [selectedId]: body }));
      await loadAnalyses();
      setDetailReloadKey((current) => current + 1);
    } catch (requestError) {
      if (!requestError.sessionExpired) setAiErrorsById((current) => ({ ...current, [selectedId]: requestError.message || 'AI 분석 요청에 실패했습니다.' }));
    } finally {
      setOpenAiApiKey('');
      if (!unauthenticatedRef.current) setAiLoadingById((current) => ({ ...current, [selectedId]: false }));
    }
  }

  async function checkBackendConnection() {
    setHelloLoading(true);
    setHelloMessage('');
    setHelloError('');
    try {
      const response = await fetch(apiUrl('/api/hello'), { credentials: 'include' });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || `요청 실패 (HTTP ${response.status})`);
      setHelloMessage(data.message);
    } catch (requestError) {
      setHelloError(requestError.message || '백엔드 연결 중 오류가 발생했습니다.');
    } finally {
      setHelloLoading(false);
    }
  }

  async function submitAnalysis(event) {
    event.preventDefault();
    setAnalysisResult(null);
    setAnalysisError('');
    setAnalysisStatus('');
    if (!errorMessage.trim()) {
      setAnalysisError('오류 메시지를 입력해 주세요.');
      return;
    }
    if (errorMessage.length > 10_000) {
      setAnalysisError('오류 메시지는 10,000자 이하여야 합니다.');
      return;
    }

    setSubmitting(true);
    setAnalysisStatus('백엔드에 요청을 보내는 중…');
    try {
      const response = await apiFetch('/api/analyses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ language, errorMessage }),
      });
      let body;
      try {
        body = await response.json();
      } catch {
        throw new Error('백엔드 응답을 읽을 수 없습니다.');
      }
      if (!response.ok) {
        const details = body.errors?.map((item) => `${item.field}: ${item.reason}`).join('\n');
        throw new Error(details || body.message || `요청 실패 (HTTP ${response.status})`);
      }
      setAnalysisResult(body);
      setAnalysisStatus(body.success ? '요청이 접수되었습니다. 실제 분석은 아직 수행되지 않았습니다.' : '요청이 완료되지 않았습니다.');
      await loadAnalyses();
    } catch (requestError) {
      if (!requestError.sessionExpired) {
        setAnalysisStatus('요청 오류');
        setAnalysisError(requestError.message || '백엔드에 연결할 수 없습니다. 실행 상태를 확인해 주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function renderDetail() {
    if (detailLoading) {
      return <section className="detail-state" role="status"><h2>분석 정보를 불러오는 중</h2></section>;
    }
    if (detailError) {
      return <section className="detail-state error-state" role="alert"><h2>분석 정보를 불러오지 못했습니다</h2><p>{detailError}</p><div className="detail-actions"><button className="button button-quiet" onClick={() => setDetailReloadKey((current) => current + 1)} type="button">다시 시도</button><button className="button button-quiet" onClick={returnToList} type="button">목록으로</button></div></section>;
    }
    if (listError) {
      return <section className="detail-state error-state" role="alert"><h2>분석 정보를 불러오지 못했습니다</h2><p>{listError}</p><button className="button button-quiet" onClick={loadAnalyses} type="button">다시 시도</button></section>;
    }
    if (!selectedAnalysis) {
      return <section className="detail-state"><h2>분석 요청을 찾을 수 없습니다</h2><p>목록에서 요청을 다시 선택해 주세요.</p><button className="button button-quiet" onClick={returnToList} type="button">목록으로</button></section>;
    }

    const notes = resolutionNote;
    const parseResult = parseResultsById[selectedId];
    const aiResult = aiResultsById[selectedId] ?? selectedAnalysis.aiAnalysisResult;
    return (
      <>
        <div className="detail-back-row"><button className="back-link" type="button" onClick={returnToList}>← 분석 목록</button><span>요청 #{selectedAnalysis.id}</span></div>
        <section className="detail-heading"><div><p className="eyebrow">ANALYSIS REQUEST</p><h1>오류 상세</h1><p>오류 정보와 해결 과정을 확인하고 기록합니다.</p></div><span className={`status-badge status-${String(selectedAnalysis.analysisStatus || '').toLowerCase()}`}><i />{statusLabel(selectedAnalysis.analysisStatus)}</span></section>
        <div className="detail-actions">
          {deleteError && <p className="error" role="alert">{deleteError}</p>}
          {!deleteConfirmationOpen ? <button className="button button-danger" type="button" onClick={() => { setDeleteError(''); setDeleteConfirmationOpen(true); }}>분석 삭제</button> : <section className="delete-confirmation" role="alertdialog" aria-labelledby="delete-confirmation-title">
            <div><h2 id="delete-confirmation-title">분석 내역을 삭제할까요?</h2><p>삭제한 내역은 복구할 수 없습니다.</p></div>
            <button className="button button-quiet" type="button" onClick={() => setDeleteConfirmationOpen(false)} disabled={deleting}>취소</button>
            <button className="button button-danger" type="button" onClick={deleteSelectedAnalysis} disabled={deleting}>{deleting ? '삭제 중…' : '삭제 확인'}</button>
          </section>}
        </div>
        <section className="detail-meta-grid" aria-label="요청 정보">
          <article><span>오류 ID</span><strong>#{selectedAnalysis.id}</strong></article>
          <article><span>언어</span><strong>{selectedAnalysis.language}</strong></article>
          <article><span>등록 일시</span><strong>{formatDate(selectedAnalysis.createdAt)}</strong></article>
          <article><span>상태</span><strong>{statusLabel(selectedAnalysis.analysisStatus)}</strong></article>
          {selectedAnalysis.completedAt && <article><span>AI 분석 완료</span><strong>{formatDate(selectedAnalysis.completedAt)}</strong></article>}
        </section>
        <section className="panel detail-panel">
          <div className="detail-section-heading"><div><h2>오류 메시지</h2><p>전체 메시지를 확인하거나 클립보드에 복사할 수 있습니다.</p></div><button className="button button-quiet" type="button" onClick={copyErrorMessage}>복사</button></div>
          <pre className="full-error-message">{selectedAnalysis.errorMessage || '오류 메시지가 없습니다.'}</pre>
          {copyStatus && <p className="copy-status" role="status">{copyStatus}</p>}
        </section>
        <section className="panel log-input-panel">
          <div className="detail-section-heading"><div><h2>원본 로그 입력</h2><p>로그는 이 화면의 메모리에만 유지되며 서버에는 저장되지 않습니다.</p></div><span className="temporary-label">최대 50,000자</span></div>
          <div className="log-input-body">
            <label htmlFor={`raw-logs-${selectedId}`}>여러 줄 로그
              <textarea id={`raw-logs-${selectedId}`} value={logTextsById[selectedId] || ''} onChange={(event) => { const value = event.target.value; setLogTextsById((current) => ({ ...current, [selectedId]: value })); setParseResultsById((current) => ({ ...current, [selectedId]: null })); setParseErrorsById((current) => ({ ...current, [selectedId]: '' })); setAiResultsById((current) => ({ ...current, [selectedId]: null })); setAiErrorsById((current) => ({ ...current, [selectedId]: '' })); }} rows={9} maxLength={50_000} placeholder={'예:\n2026-10-30 10:00:00 ERROR 요청 처리 실패\njava.lang.IllegalStateException: 처리 실패\n    at com.example.Service.run(Service.java:42)'} />
            </label>
            <div className="log-submit-row"><span>{(logTextsById[selectedId] || '').length.toLocaleString()} / 50,000자</span><button className="button button-primary" type="button" onClick={parseLog} disabled={Boolean(parseLoadingById[selectedId])}>{parseLoadingById[selectedId] ? '추출 중…' : '핵심 정보 추출'}</button></div>
            {parseErrorsById[selectedId] && <p className="error" role="alert">{parseErrorsById[selectedId]}</p>}
          </div>
        </section>
        {parseResult && <section className="panel masked-log-panel" aria-label="민감정보 탐지 및 마스킹 결과">
          <div className="detail-section-heading"><div><h2>민감정보 탐지 결과</h2><p>탐지된 값은 마스킹되어 표시됩니다. 원본 입력 영역과 구분한 미리보기입니다.</p></div><span className="temporary-label">로컬 규칙 검사</span></div>
          <div className="masking-content">
            {parseResult.sensitiveDetections?.length ? <ul className="detection-list">{parseResult.sensitiveDetections.map((item) => <li key={`${item.lineNumber}-${item.type}`}><span>{item.type}</span><span>로그 {item.lineNumber}행</span><span>{item.occurrences}건</span></li>)}</ul> : <p className="no-detections">대표 패턴을 탐지하지 못했습니다. 민감정보가 없다는 의미는 아닙니다.</p>}
            <div className="masked-preview"><h3>마스킹된 로그 미리보기</h3><pre>{parseResult.maskedLogPreview}</pre></div>
            <p className="masking-warning" role="note">자동 탐지에서 놓친 정보가 있을 수 있습니다. AI 분석 실행을 누르면 서버가 로그를 다시 마스킹한 뒤 OpenAI로 전송합니다. 사용량에 따라 API 비용이 발생할 수 있습니다.</p>
          </div>
        </section>}        {parseResult && <section className="panel extracted-panel" aria-label="로그에서 추출한 정보">
            <div className="detail-section-heading"><div><h2>파서 추출 정보</h2><p>패턴에 일치한 로그 신호입니다. 원인이나 근본 원인을 확정하지 않습니다.</p></div><div className="topbar-actions"><span className="temporary-label">규칙 기반 추출</span><button className="button button-quiet" type="button" onClick={copyParsedSummary}>분석 결과 복사</button></div></div>
          <div className="extracted-grid">
            <article><h3>대표 오류</h3>{parseResult.representativeError ? <pre className="extracted-line">L{parseResult.representativeError.lineNumber}: {parseResult.representativeError.text}</pre> : <p className="muted-copy">인식한 대표 오류가 없습니다.</p>}</article>
            <article><h3>연관 예외 (Caused by)</h3>{parseResult.relatedExceptions?.length ? <ul className="extracted-list">{parseResult.relatedExceptions.map((item) => <li key={`${item.lineNumber}-${item.text}`}>L{item.lineNumber}: {item.text}</li>)}</ul> : <p className="muted-copy">추출된 연관 예외가 없습니다.</p>}</article>
            <article><h3>Java 스택 위치</h3>{parseResult.sourceLocations?.length ? <ul className="extracted-list">{parseResult.sourceLocations.map((item) => <li key={`${item.lineNumber}-${item.fileName}-${item.stackFrame}`}>L{item.logLineNumber}: {item.fileName}:{item.sourceLineNumber} — {item.stackFrame}</li>)}</ul> : <p className="muted-copy">파일명과 줄 번호가 포함된 프레임이 없습니다.</p>}</article>
            <article><h3>핵심 로그</h3>{parseResult.keyLogs?.length ? <pre className="extracted-lines">{parseResult.keyLogs.map((item) => `L${item.lineNumber}: ${item.text}`).join('\n')}</pre> : <p className="muted-copy">ERROR, 예외 또는 Java 스택 프레임을 찾지 못했습니다.</p>}</article>
            <article className="context-preview"><h3>주변 문맥 미리보기</h3>{parseResult.contextPreview?.length ? <pre className="extracted-lines">{parseResult.contextPreview.map((item) => `L${item.lineNumber}: ${item.text}`).join('\n')}</pre> : <p className="muted-copy">표시할 문맥이 없습니다.</p>}</article>
          </div>
            {parseCopyStatus && <p className={`copy-status ${parseCopyStatus.startsWith('복사하지') || parseCopyStatus.startsWith('복사할') ? 'error' : ''}`} role={parseCopyStatus.startsWith('복사하지') || parseCopyStatus.startsWith('복사할') ? 'alert' : 'status'}>{parseCopyStatus}</p>}
        </section>}
        <section className="analysis-sections"  aria-label="분석 결과">
          {!aiResult && <article className="panel analysis-placeholder"><div className="detail-section-heading"><div><h2>쉬운 설명</h2><p>오류를 이해하기 쉬운 말로 설명합니다.</p></div></div><div className="not-analyzed"><span>분석 전</span><p>아직 분석 결과가 없습니다.</p></div></article>}
          {!aiResult && <article className="panel analysis-placeholder"><div className="detail-section-heading"><div><h2>기술적 분석</h2><p>원인 후보와 관련 기술 정보를 정리합니다.</p></div></div><div className="not-analyzed"><span>분석 전</span><p>아직 분석 결과가 없습니다.</p></div></article>}
          <article className="panel analysis-placeholder"><div className="detail-section-heading"><div><h2>AI 사용 설정</h2><p>규칙 기반 파싱과 마스킹은 AI 설정과 관계없이 사용할 수 있습니다.</p></div></div>
            <div className="ai-settings">
              <label className="ai-toggle"><input type="checkbox" checked={aiUseSelected} disabled={Boolean(aiLoadingById[selectedId])} onChange={(event) => { const enabled = event.target.checked; setAiUseSelected(enabled); if (!enabled) setOpenAiApiKey(''); }} />마스킹 로그를 OpenAI로 보내는 데 동의</label>
              {aiUseSelected ? <>
                <label className="ai-key-field">OpenAI API 키<input type="password" autoComplete="off" maxLength={4096} value={openAiApiKey} onChange={(event) => setOpenAiApiKey(event.target.value)} placeholder="API 키 입력" /></label>
                <div className="ai-key-actions"><span>{openAiApiKey ? '키 입력됨 · 연결 확인 안 됨' : 'API 키 미등록'}</span><button className="button button-quiet" type="button" disabled={!openAiApiKey} onClick={() => setOpenAiApiKey('')}>키 삭제</button></div>
                <p>키는 이 화면의 메모리에만 보관되며 분석 요청 후 지워집니다. 개발 Mock 모드에서는 키 없이 테스트할 수 있고, 일반 모드에서는 API 키가 필요합니다.</p>
                <button className="button button-primary" type="button" onClick={analyzeWithAi} disabled={!logTextsById[selectedId]?.trim() || Boolean(aiLoadingById[selectedId])}>{aiLoadingById[selectedId] ? 'AI 분석 중…' : 'AI 분석 실행'}</button>
              </> : <p className="ai-setting-note">AI 미사용 상태입니다. 기존 로그 분석·마스킹 기능은 계속 사용할 수 있습니다.</p>}
              {aiErrorsById[selectedId] && <p className="error" role="alert">{aiErrorsById[selectedId]}</p>}
              <p className="ai-billing-note">실행하면 로그와 파서 추출 결과를 OpenAI API에 전송합니다. Responses API 응답 저장은 비활성화하지만, 계정 설정에 따라 abuse-monitoring 로그에는 요청·응답이 최대 30일 보관될 수 있습니다. ChatGPT 구독과 별도로 API 사용 비용이 발생합니다.</p>
            </div>
          </article>
        </section>
        {aiResult && <section className="panel ai-result-panel" aria-label="AI 분석 결과">
          <div className="detail-section-heading"><div><h2>AI 분석 결과</h2><p>로그에서 관찰한 사실과 원인 추정을 분리했습니다. 원인 후보는 확정 진단이 아닙니다.</p></div><span className="temporary-label">확신도: {aiResult.confidenceLevel}</span></div>
          <div className="ai-result-content">
            <article><h3>오류 요약</h3><p>{aiResult.summary}</p></article>
            <article><h3>가능한 원인 <small>추정</small></h3>{aiResult.possibleCauses?.length ? <ul>{aiResult.possibleCauses.map((item, index) => <li key={index}>{item}</li>)}</ul> : <p>원인 후보가 없습니다.</p>}</article>
            <article><h3>로그 근거 <small>관찰 사실과 해석</small></h3>{aiResult.logEvidence?.length ? <ul>{aiResult.logEvidence.map((item, index) => <li key={index}><strong>로그에서 확인:</strong> {item.observedFact}<br /><strong>해석(추정):</strong> {item.interpretation}</li>)}</ul> : <p>확인 가능한 근거가 없습니다.</p>}</article>
            <article><h3>해결 방법</h3>{aiResult.solutions?.length ? <ul>{aiResult.solutions.map((item, index) => <li key={index}>{item}</li>)}</ul> : <p>제안된 해결 방법이 없습니다.</p>}</article>
            <article><h3>수정 코드 예시</h3><pre>{aiResult.codeExample || '적용할 코드 예시가 없습니다.'}</pre></article>
            <article><h3>검증 방법</h3>{aiResult.verificationSteps?.length ? <ol>{aiResult.verificationSteps.map((item, index) => <li key={index}>{item}</li>)}</ol> : <p>검증 절차가 없습니다.</p>}</article>
          </div>
        </section>}
        <section className="panel notes-panel">
          <div className="detail-section-heading"><div><h2>해결 과정 기록</h2><p>실제 원인부터 수정 후 테스트 결과까지 기록합니다.</p></div><span className="temporary-label">서버 저장</span></div>
          {notesLoading ? <p className="copy-status" role="status">해결 기록을 불러오는 중…</p> : notesLoadError ? <div className="note-fields"><p className="error" role="alert">{notesLoadError}</p><button className="button button-quiet" type="button" onClick={() => setDetailReloadKey((current) => current + 1)}>다시 시도</button></div> : notes && <>
            <div className="note-fields">
              <label>실제 원인<textarea value={notes.rootCause || ''} onChange={(event) => updateResolutionNote('rootCause', event.target.value)} rows={3} maxLength={20_000} placeholder="확인한 실제 오류 원인을 기록하세요." /></label>
              <label>해결 과정<textarea value={notes.attemptedSteps || ''} onChange={(event) => updateResolutionNote('attemptedSteps', event.target.value)} rows={5} maxLength={50_000} placeholder="해결을 위해 시도한 과정을 기록하세요." /></label>
              <label>최종 해결 방법<textarea value={notes.finalSolution || ''} onChange={(event) => updateResolutionNote('finalSolution', event.target.value)} rows={4} maxLength={20_000} placeholder="최종적으로 적용한 해결 방법을 기록하세요." /></label>
              <label>테스트 결과<textarea value={notes.testResult || ''} onChange={(event) => updateResolutionNote('testResult', event.target.value)} rows={4} maxLength={20_000} placeholder="수정 후 테스트 결과를 기록하세요." /></label>
              <label>해결 상태<select value={notes.resolutionStatus || 'UNRESOLVED'} onChange={(event) => updateResolutionNote('resolutionStatus', event.target.value)}>
                <option value="UNRESOLVED">미해결</option>
                <option value="IN_PROGRESS">진행 중</option>
                <option value="RESOLVED">해결 완료</option>
              </select></label>
              <div className="form-actions">
                <div>{notesSaveMessage && <p role="status">{notesSaveMessage}</p>}{notesSaveError && <p className="error" role="alert">{notesSaveError}</p>}</div>
                <button className="button button-primary" type="button" onClick={saveResolutionNote} disabled={notesSaving}>{notesSaving ? '저장 중…' : '해결 기록 저장'}</button>
              </div>
            </div>
            {notes.updatedAt && <p className="copy-status">마지막 저장: {formatDate(notes.updatedAt)}</p>}
          </>}
        </section>
      </>
    );
  }

  if (authStatus === 'checking') {
    return <main className="auth-shell"><section className="auth-card" role="status"><div className="auth-brand"><span className="brand-mark">D</span><span>DevTrace</span></div><h1>로그인 상태 확인 중</h1><p className="auth-description">안전한 세션을 확인하고 있습니다.</p></section></main>;
  }

  if (authStatus === 'error') {
    return <main className="auth-shell"><section className="auth-card" role="alert"><div className="auth-brand"><span className="brand-mark">D</span><span>DevTrace</span></div><p className="eyebrow">CONNECTION STATUS</p><h1>로그인 상태를 확인할 수 없습니다</h1><p className="auth-description">{authServiceError || '서버에 연결할 수 없습니다.'}</p><button className="button button-primary auth-submit" type="button" onClick={() => window.location.reload()}>다시 연결</button></section></main>;
  }

  if (authStatus !== 'authenticated') {
    return <AuthScreen mode={authMode} onModeChange={(mode) => { setAuthMode(mode); setAuthError(''); setAuthNotice(''); }} onSubmit={submitAuth} busy={authBusy} error={authError} notice={authNotice} />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" type="button" onClick={returnToList} aria-label="DevTrace 대시보드"><span className="brand-mark">D</span><span>DevTrace</span></button>
        <p className="nav-caption">WORKSPACE</p>
        <button className={`nav-item ${selectedId === null ? 'active' : ''}`} onClick={returnToList} type="button"><span className="nav-icon">▤</span>분석 내역</button>
        <div className="sidebar-footer"><span className="status-dot" /> API 연결 대기</div>
      </aside>
      <main className="main-content" id="dashboard">
        <header className="topbar"><div className="breadcrumb">워크스페이스 <span>/</span> {selectedId === null ? '분석 대시보드' : '오류 상세'}</div><div className="topbar-actions"><span className="signed-in-user">{authUser?.nickname || authUser?.email}</span><button className="button button-quiet" type="button" onClick={logout}>로그아웃</button><button className="button button-quiet" type="button" onClick={checkBackendConnection} disabled={helloLoading}>{helloLoading ? '확인 중…' : '백엔드 연결 확인'}</button>{selectedId === null && <button className="button button-primary" type="button" onClick={() => document.getElementById('new-analysis')?.scrollIntoView({ behavior: 'smooth' })}>＋ 새 분석</button>}</div></header>
        <div className="content-wrap">
          {sessionNotice && <p className="inline-notice error" role="alert">{sessionNotice}</p>}
          {helloMessage && <p className="inline-notice success-message" role="status">{helloMessage}</p>}{helloError && <p className="inline-notice error" role="alert">{helloError}</p>}
          {selectedId !== null ? renderDetail() : <>
            <section className="welcome-row"><div><p className="eyebrow">DEVTRACE WORKSPACE</p><h1>분석 대시보드</h1><p className="page-description">오류 접수 현황과 분석 요청을 한곳에서 관리하세요.</p></div><button className="button button-quiet refresh-button" type="button" onClick={loadAnalyses} disabled={listLoading}><span aria-hidden="true">↻</span> {listLoading ? '불러오는 중…' : '새로고침'}</button></section>
            <section className="stats-grid" aria-label="분석 요청 요약"><article className="stat-card"><div className="stat-top"><span>전체 요청</span><span className="stat-icon blue">▤</span></div><strong>{analyses.length}</strong><small>저장된 전체 분석 요청</small></article><article className="stat-card"><div className="stat-top"><span>대기 중</span><span className="stat-icon amber">◷</span></div><strong>{pendingCount}</strong><small>분석 처리를 기다리는 요청</small></article><article className="stat-card"><div className="stat-top"><span>완료</span><span className="stat-icon green">✓</span></div><strong>{completedCount}</strong><small>완료 상태로 기록된 요청</small></article></section>
            <section className="panel list-panel" id="analyses"><div className="panel-heading"><div><h2>분석 요청</h2><p>최근 요청이 먼저 표시됩니다.</p></div><label className="search-box"><span aria-hidden="true">⌕</span><input value={searchTerm} onChange={(event) => setSearchTerm(event.target.value)} placeholder="언어 또는 오류 메시지 검색" aria-label="언어 또는 오류 메시지 검색" /></label></div>
              {listError ? <div className="state-message error-state" role="alert"><span className="state-symbol">!</span><h3>목록을 불러오지 못했습니다</h3><p>{listError}</p><button className="button button-quiet" onClick={loadAnalyses} type="button">다시 시도</button></div> : listLoading && analyses.length === 0 ? <div className="state-message" role="status"><span className="state-symbol">…</span><h3>분석 목록을 불러오는 중</h3><p>저장된 요청을 확인하고 있습니다.</p></div> : filteredAnalyses.length === 0 ? <div className="state-message"><span className="state-symbol">⌕</span><h3>{analyses.length ? '검색 결과가 없습니다' : '아직 분석 요청이 없습니다'}</h3><p>{analyses.length ? '다른 언어나 오류 메시지로 검색해 보세요.' : '새 분석을 등록하면 이곳에 표시됩니다.'}</p></div> : <div className="table-scroll"><table><thead><tr><th>요청</th><th>언어</th><th>오류 메시지</th><th>상태</th><th>생성 시각</th></tr></thead><tbody>{filteredAnalyses.map((item) => <tr className="analysis-row" key={item.id} onClick={() => openAnalysis(item)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') openAnalysis(item); }} tabIndex={0} aria-label={`요청 ${item.id} 상세 보기`}><td className="id-cell">#{item.id}</td><td><span className="language-chip">{item.language}</span></td><td className="message-cell"><span>{item.errorMessage}</span></td><td><span className={`status-badge status-${String(item.analysisStatus || '').toLowerCase()}`}><i />{statusLabel(item.analysisStatus)}</span></td><td className="date-cell">{formatDate(item.createdAt)}</td></tr>)}</tbody></table></div>}
              {listLoading && analyses.length > 0 && <p className="updating-note" role="status">목록을 업데이트하고 있습니다…</p>}
            </section>
            <section className="panel form-panel" id="new-analysis"><div className="panel-heading form-heading"><div><p className="eyebrow">NEW REQUEST</p><h2>새 분석 요청</h2><p>오류 정보를 입력해 백엔드에 전달합니다.</p></div></div><form className="analysis-form" onSubmit={submitAnalysis}><div className="form-field"><label htmlFor="language">프로그래밍 언어</label><select id="language" value={language} onChange={(event) => setLanguage(event.target.value)}>{languages.map((item) => <option key={item} value={item}>{item}</option>)}</select></div><div className="form-field"><div className="label-row"><label htmlFor="error-message">오류 메시지</label><span>{errorMessage.length.toLocaleString()} / 10,000자</span></div><textarea id="error-message" value={errorMessage} onChange={(event) => setErrorMessage(event.target.value)} placeholder="예: NullPointerException at UserService.java:25" rows={7} maxLength={10_000} required /></div><div className="form-actions"><div>{analysisStatus && <p role="status">{analysisStatus}</p>}{analysisError && <p className="error" role="alert">{analysisError}</p>}</div><button className="button button-primary" type="submit" disabled={submitting}>{submitting ? '전송 중…' : '분석 요청 보내기'}</button></div>{analysisResult?.data && <section className="result" aria-label="백엔드 응답 결과"><h3>{analysisResult.message}</h3><dl><dt>언어</dt><dd>{analysisResult.data.language}</dd><dt>문자 수</dt><dd>{analysisResult.data.characterCount}</dd><dt>분석 상태</dt><dd>{analysisResult.data.analysisStatus}</dd><dt>전달된 오류 메시지</dt><dd><pre>{analysisResult.data.receivedErrorMessage}</pre></dd></dl></section>}</form></section>
          </>}
          <footer className="page-footer">DevTrace <span>·</span> 오류 분석 워크스페이스</footer>
        </div>
      </main>
    </div>
  );
}




