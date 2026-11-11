import { useEffect, useState } from 'react'
import { ArrowRight, BookOpen, Check, CheckCheck, Clock3, FileText, Flame, GraduationCap, LayoutDashboard, ListTodo, LoaderCircle, LogOut, Menu, Plus, Send, Sparkles, SquarePen, Target, Trash2, X } from 'lucide-react'
import { api } from './api'

const NAV = [['overview', 'Overview', LayoutDashboard], ['tasks', 'My tasks', ListTodo], ['subjects', 'Subjects', BookOpen], ['notes', 'Notes', FileText], ['assistant', 'AI assistant', Sparkles]]
const COLORS = ['#88a891', '#d38a61', '#7192a3', '#c49d52', '#ad8290', '#829979']
const today = new Date()
const todayKey = today.toISOString().slice(0, 10)
const EMPTY = { task: { title: '', details: '', subjectId: '', priority: 'MEDIUM', dueDate: '', status: 'TODO' }, subject: { name: '', description: '', color: COLORS[0] }, note: { title: '', content: '', subjectId: '' } }
const dateLabel = (date) => date ? new Date(`${date}T00:00:00`).toLocaleDateString('en', { month: 'short', day: 'numeric' }) : 'No due date'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('studymate-token') || '')
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem('studymate-user') || 'null'))
  const [page, setPage] = useState('overview')
  const [records, setRecords] = useState({ dashboard: null, tasks: [], subjects: [], notes: [], plans: [] })
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [authMode, setAuthMode] = useState('login')
  const [auth, setAuth] = useState({ name: '', email: '', password: '' })
  const [modal, setModal] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const [mobileNav, setMobileNav] = useState(false)
  const [assistant, setAssistant] = useState({ prompt: '', mode: 'explain' })
  const [answer, setAnswer] = useState('')
  const [plans, setPlans] = useState({ startDate: todayKey, days: 7, hoursPerDay: 3, focus: '' })

  async function refresh(currentToken = token) {
    if (!currentToken) return
    setLoading(true)
    setError('')
    try {
      const [dashboard, tasks, subjects, notes, savedPlans] = await Promise.all([
        api('/dashboard', { token: currentToken }), api('/tasks', { token: currentToken }), api('/subjects', { token: currentToken }),
        api('/notes', { token: currentToken }), api('/ai/plans', { token: currentToken }),
      ])
      setRecords({ dashboard, tasks, subjects, notes, plans: savedPlans })
    } catch (err) {
      setError(err.message)
      if (err.message.includes('401')) signOut()
    } finally { setLoading(false) }
  }

  useEffect(() => { if (token) refresh(token) }, [token])
  useEffect(() => {
    if (!notice) return undefined
    const timer = setTimeout(() => setNotice(''), 2600)
    return () => clearTimeout(timer)
  }, [notice])

  function signOut() {
    localStorage.removeItem('studymate-token'); localStorage.removeItem('studymate-user')
    setToken(''); setUser(null); setRecords({ dashboard: null, tasks: [], subjects: [], notes: [], plans: [] })
  }

  async function authenticate(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await api(`/auth/${authMode === 'register' ? 'register' : 'login'}`, {
        method: 'POST', body: authMode === 'register' ? auth : { email: auth.email, password: auth.password },
      })
      localStorage.setItem('studymate-token', result.token); localStorage.setItem('studymate-user', JSON.stringify(result.user))
      setUser(result.user); setToken(result.token)
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  function openModal(type, value = null) { setModal({ type, value: value ? { ...value } : { ...EMPTY[type] } }) }

  async function saveRecord(event) {
    event.preventDefault(); setBusy(true)
    try {
      const { type, value } = modal
      const payload = { ...value }; delete payload.id
      if ('subjectId' in payload && payload.subjectId === '') payload.subjectId = null
      if (payload.dueDate === '') payload.dueDate = null
      const path = type === 'task' ? '/tasks' : type === 'subject' ? '/subjects' : '/notes'
      await api(`${path}${value.id ? `/${value.id}` : ''}`, { method: value.id ? 'PUT' : 'POST', body: payload, token })
      setModal(null); setNotice(`${type[0].toUpperCase()}${type.slice(1)} saved`); await refresh()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function remove(type, id) {
    if (!window.confirm(`Delete this ${type}?`)) return
    const path = type === 'task' ? '/tasks' : type === 'subject' ? '/subjects' : '/notes'
    try { await api(`${path}/${id}`, { method: 'DELETE', token }); setNotice(`${type} deleted`); await refresh() }
    catch (err) { setError(err.message) }
  }

  async function toggleTask(task) {
    try {
      await api(`/tasks/${task.id}`, { method: 'PUT', token, body: { ...task, status: task.status === 'COMPLETED' ? 'TODO' : 'COMPLETED' } })
      setNotice(task.status === 'COMPLETED' ? 'Task moved back to your list' : 'One less thing to worry about'); await refresh()
    } catch (err) { setError(err.message) }
  }

  async function askAI(event) {
    event.preventDefault(); if (!assistant.prompt.trim()) return
    setBusy(true); setError(''); setAnswer('')
    try { const result = await api('/ai/ask', { method: 'POST', token, body: assistant }); setAnswer(result.answer) }
    catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function makePlan(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await api('/ai/plan', { method: 'POST', token, body: { ...plans, days: Number(plans.days), hoursPerDay: Number(plans.hoursPerDay) } })
      setRecords((current) => ({ ...current, plans: [result, ...current.plans] })); setNotice('Your new study plan is ready')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  if (!token) return <AuthScreen mode={authMode} setMode={setAuthMode} form={auth} setForm={setAuth} onSubmit={authenticate} busy={busy} error={error} />
  const active = records.tasks.filter((task) => task.status !== 'COMPLETED')
  const pageLabel = NAV.find(([id]) => id === page)?.[1]

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
      <a className="brand" href="#overview" onClick={(event) => { event.preventDefault(); setPage('overview') }}><span className="brand-mark"><GraduationCap size={21} /></span><span className="brand-name">study<span>mate</span></span><b>.</b></a>
      <span className="workspace-label">YOUR WORKSPACE</span>
      <nav className="side-nav">{NAV.map(([id, label, Icon]) => <button key={id} className={`nav-link ${page === id ? 'nav-active' : ''}`} onClick={() => { setPage(id); setMobileNav(false) }}><Icon size={18} /><span>{label}</span>{id === 'tasks' && active.length > 0 && <small className="nav-count">{active.length}</small>}{id === 'assistant' && <small className="nav-new">AI</small>}</button>)}</nav>
      <div className="sidebar-spacer" /><div className="sidebar-quote"><Sparkles size={15} /><p>A little progress each day adds up to big results.</p><small>KEEP GOING</small></div>
      <button className="profile-button" onClick={signOut} title="Sign out"><span className="avatar">{user?.name?.[0]?.toUpperCase() || 'S'}</span><span className="profile-copy"><strong>{user?.name || 'Student'}</strong><small>{user?.email}</small></span><LogOut size={16} /></button>
    </aside>
    {mobileNav && <button className="nav-backdrop" aria-label="Close menu" onClick={() => setMobileNav(false)} />}
    <main className="main-area"><header className="topbar"><button className="icon-button mobile-menu" title="Open navigation" onClick={() => setMobileNav(true)}><Menu size={19} /></button><div className="breadcrumb"><span>Workspace</span><i>/</i><strong>{pageLabel}</strong></div><div className="topbar-right"><span>{today.toLocaleDateString('en', { weekday: 'long', month: 'long', day: 'numeric' })}</span><span className="topbar-avatar">{user?.name?.[0]?.toUpperCase() || 'S'}</span></div></header>
      <section className="page-content" key={page}>
        {error && <div className="alert" role="alert"><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss"><X size={16} /></button></div>}
        {loading ? <div className="loading-inline"><LoaderCircle className="spin" size={18} /> Getting your workspace ready...</div> : <>
          {page === 'overview' && <Overview records={records} user={user} onNavigate={setPage} onNew={() => openModal('task')} onToggle={toggleTask} onEdit={(task) => openModal('task', task)} />}
          {page === 'tasks' && <TasksPage tasks={records.tasks} filter={filter} setFilter={setFilter} onNew={() => openModal('task')} onToggle={toggleTask} onEdit={(task) => openModal('task', task)} onDelete={(id) => remove('task', id)} />}
          {page === 'subjects' && <SubjectsPage subjects={records.subjects} onNew={() => openModal('subject')} onEdit={(item) => openModal('subject', item)} onDelete={(id) => remove('subject', id)} onNavigate={setPage} />}
          {page === 'notes' && <NotesPage notes={records.notes} onNew={() => openModal('note')} onEdit={(item) => openModal('note', item)} onDelete={(id) => remove('note', id)} />}
          {page === 'assistant' && <AssistantPage assistant={assistant} setAssistant={setAssistant} answer={answer} busy={busy} onAsk={askAI} plans={plans} setPlans={setPlans} savedPlans={records.plans} onPlan={makePlan} />}
        </>}
      </section>
    </main>
    {modal && <RecordModal modal={modal} setModal={setModal} subjects={records.subjects} busy={busy} onSubmit={saveRecord} onDelete={remove} />}
    {notice && <div className="toast" role="status"><Check size={16} />{notice}</div>}
  </div>
}

function AuthScreen({ mode, setMode, form, setForm, onSubmit, busy, error }) {
  const register = mode === 'register'
  return <main className="auth-shell"><section className="auth-art"><div className="auth-art-top"><a className="brand brand-light" href="#"><span className="brand-mark"><GraduationCap size={21} /></span><span className="brand-name">study<span>mate</span></span><b>.</b></a><span className="art-index">STUDY, WITH INTENTION</span></div><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-content"><span className="art-kicker"><Sparkles size={14} /> YOUR SPACE TO GROW</span><h1>Make room<br />for your <em>next</em><br />big idea.</h1><p>A little more focus. A lot less juggling. Your studies, finally in one place.</p></div><div className="art-bottom"><span>01 — PLAN YOUR DAY</span><span>02 — FIND YOUR FOCUS</span><span>03 — SEE HOW FAR YOU'VE COME</span></div><span className="auth-decoration">S.</span></section><section className="auth-panel"><div className="auth-form-wrap"><div className="auth-mobile-brand"><GraduationCap size={22} /> studymate.</div><span className="eyebrow">{register ? 'START FRESH' : 'GOOD TO HAVE YOU BACK'}</span><h2>{register ? 'Create your account' : 'Welcome back'}</h2><p className="auth-subtitle">{register ? 'Your next chapter starts right here.' : 'Pick up right where your best thinking happens.'}</p><form className="auth-form" onSubmit={onSubmit}>{register && <label>Your name<input autoComplete="name" required maxLength="100" placeholder="e.g. Alex Morgan" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>}<label>Email address<input type="email" autoComplete="email" required placeholder="you@example.com" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label><label>Password<input type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 8 : undefined} required placeholder={register ? 'At least 8 characters' : 'Enter your password'} value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>{error && <div className="auth-error" role="alert">{error}</div>}<button className="button button-primary auth-submit" disabled={busy}>{busy && <LoaderCircle className="spin" size={17} />}{register ? 'Create account' : 'Sign in'}<ArrowRight size={17} /></button></form><p className="auth-switch">{register ? 'Already have an account?' : 'New around here?'} <button onClick={() => setMode(register ? 'login' : 'register')}>{register ? 'Sign in' : 'Create an account'}</button></p><div className="auth-footnote"><i /> Your work is private, secure, and yours.</div></div></section></main>
}

function Overview({ records, user, onNavigate, onNew, onToggle, onEdit }) {
  const first = user?.name?.trim().split(/\s+/)[0] || 'there'
  const hour = new Date().getHours()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
  const upcoming = [...records.tasks].filter((task) => task.status !== 'COMPLETED').sort((a, b) => (a.dueDate || '9999').localeCompare(b.dueDate || '9999')).slice(0, 4)
  const next = upcoming.find((task) => task.dueDate)
  const dueToday = records.tasks.filter((task) => task.dueDate === todayKey && task.status !== 'COMPLETED').length
  const dashboard = records.dashboard || {}
  const completion = dashboard.totalTasks ? Math.round(dashboard.completedTasks / dashboard.totalTasks * 100) : 0
  return <><div className="welcome-row"><div><span className="eyebrow"><i className="eyebrow-dot" /> YOUR STUDY DESK</span><h1>{greeting}, {first}<span className="title-period">.</span></h1><p className="page-subtitle">A clear mind starts with a clear plan. Here's yours.</p></div><button className="button button-primary" onClick={onNew}><Plus size={17} /> New task</button></div>
    <div className="overview-grid"><section className="focus-banner"><div className="focus-copy"><span className="focus-label"><Flame size={14} /> YOUR NEXT FOCUS</span><h2>{next?.title || 'Build your first study rhythm'}</h2><p>{next ? `${next.subjectName || 'Personal study'} · Due ${dateLabel(next.dueDate)}` : 'Add a task to give your study session a direction.'}</p><button className="focus-link" onClick={() => next ? onEdit(next) : onNew()}>{next ? 'Pick this up' : 'Add your first task'} <ArrowRight size={15} /></button></div><div className="focus-visual"><div className="focus-circle"><Target size={34} /></div><div className="focus-orbit" /><span className="focus-small">ONE THING<br />AT A TIME</span></div><span className="focus-watermark">FOCUS</span></section>
      <section className="stat-card"><div className="stat-top"><span className="stat-label">Tasks in motion</span><span className="stat-icon icon-sage"><ListTodo size={16} /></span></div><strong className="stat-number">{dashboard.totalTasks || 0}</strong><span className="stat-caption">{dashboard.completedTasks || 0} already completed</span><div className="mini-progress"><span style={{ width: `${completion}%` }} /></div></section>
      <section className="stat-card"><div className="stat-top"><span className="stat-label">Subjects on your desk</span><span className="stat-icon icon-blue"><BookOpen size={16} /></span></div><strong className="stat-number">{records.subjects.length.toString().padStart(2, '0')}</strong><span className="stat-caption">Each one a step forward</span><button className="stat-arrow" title="View subjects" onClick={() => onNavigate('subjects')}><ArrowRight size={17} /></button></section>
      <section className="stat-card stat-card-warm"><div className="stat-top"><span className="stat-label">Due today</span><span className="stat-icon icon-terracotta"><Clock3 size={16} /></span></div><strong className="stat-number">{dueToday.toString().padStart(2, '0')}</strong><span className="stat-caption">{dueToday ? 'You have got this' : 'A little breathing room today'}</span></section>
      <section className="panel tasks-panel"><div className="panel-heading"><div><span className="eyebrow">STAY IN THE FLOW</span><h2>Coming up</h2></div><button className="text-link" onClick={() => onNavigate('tasks')}>All tasks <ArrowRight size={15} /></button></div>{upcoming.length ? <div className="upcoming-list">{upcoming.map((task) => <TaskRow key={task.id} task={task} onToggle={onToggle} onEdit={onEdit} compact />)}</div> : <EmptyState icon={CheckCheck} title="Clear skies ahead" copy="Add your first task and we'll keep it in view." action="Create a task" onClick={onNew} />}</section>
      <section className="panel subjects-panel"><div className="panel-heading"><div><span className="eyebrow">YOUR LEARNING MIX</span><h2>Subjects</h2></div><button className="icon-button" title="View subjects" onClick={() => onNavigate('subjects')}><ArrowRight size={17} /></button></div>{records.subjects.length ? <div className="subject-mini-list">{records.subjects.slice(0, 4).map((subject) => <button className="subject-mini" key={subject.id} onClick={() => onNavigate('subjects')}><i style={{ background: subject.color }} /><span>{subject.name}<small>{subject.totalTasks} {subject.totalTasks === 1 ? 'task' : 'tasks'}</small></span><b>{subject.totalTasks ? Math.round(subject.completedTasks / subject.totalTasks * 100) : 0}%</b></button>)}</div> : <EmptyState icon={BookOpen} title="Make it yours" copy="Add a subject to give your tasks a home." action="Add a subject" onClick={() => onNavigate('subjects')} />}</section></div>
    <div className="overview-footer"><span>Progress is progress, no matter the pace.</span><button onClick={() => onNavigate('assistant')}><Sparkles size={14} /> Make a study plan <ArrowRight size={14} /></button></div></>
}

function EmptyState({ icon: Icon, title, copy, action, onClick }) { return <div className="empty-state"><span className="empty-icon"><Icon size={21} /></span><h3>{title}</h3><p>{copy}</p>{action && <button className="empty-action" onClick={onClick}>{action} <ArrowRight size={14} /></button>}</div> }

function PageHeading({ eyebrow, title, subtitle, action, onAction }) {
  return <div className="welcome-row"><div><span className="eyebrow"><i className="eyebrow-dot" /> {eyebrow}</span><h1>{title}<span className="title-period">.</span></h1><p className="page-subtitle">{subtitle}</p></div>{action && <button className="button button-primary" onClick={onAction}><Plus size={17} /> {action}</button>}</div>
}

function TasksPage({ tasks, filter, setFilter, onNew, onToggle, onEdit, onDelete }) {
  const filtered = filter === 'ALL' ? tasks : tasks.filter((task) => task.status === filter)
  const groups = [['TODO', 'UP NEXT'], ['IN_PROGRESS', 'IN PROGRESS'], ['COMPLETED', 'COMPLETED']]
    .map(([id, label]) => ({ id, label, rows: filtered.filter((task) => task.status === id) })).filter((group) => group.rows.length)
  return <><PageHeading eyebrow="THE LITTLE THINGS ADD UP" title="My tasks" subtitle="One step at a time. You’re doing better than you think." action="Add task" onAction={onNew} />
    <div className="filter-bar"><div className="segmented-control">{[['ALL', 'All'], ['TODO', 'To do'], ['IN_PROGRESS', 'In progress'], ['COMPLETED', 'Done']].map(([id, label]) => <button className={filter === id ? 'segment-active' : ''} key={id} onClick={() => setFilter(id)}>{label}<span>{id === 'ALL' ? tasks.length : tasks.filter((task) => task.status === id).length}</span></button>)}</div><span className="task-total">{tasks.filter((task) => task.status !== 'COMPLETED').length} things in motion</span></div>
    {!tasks.length ? <section className="panel page-empty"><EmptyState icon={ListTodo} title="A fresh page" copy="Add a study task and take your next small step." action="Add a task" onClick={onNew} /></section> : !groups.length ? <section className="panel page-empty"><EmptyState icon={CheckCheck} title="Nothing in this list" copy="Try another filter to see your tasks." /></section> : <div className="task-groups">{groups.map((group) => <section className="panel task-group" key={group.id}><div className="group-heading"><h2>{group.label}</h2><span>{group.rows.length.toString().padStart(2, '0')}</span></div><div className="upcoming-list">{group.rows.map((task) => <TaskRow key={task.id} task={task} onToggle={onToggle} onEdit={onEdit} onDelete={onDelete} />)}</div></section>)}</div>}
  </>
}

function TaskRow({ task, onToggle, onEdit, onDelete, compact = false }) {
  const overdue = task.dueDate && task.dueDate < todayKey && task.status !== 'COMPLETED'
  return <article className={`task-row ${compact ? 'task-row-compact' : ''} ${task.status === 'COMPLETED' ? 'task-is-done' : ''}`}><button className={`task-check ${task.status === 'COMPLETED' ? 'task-checked' : ''}`} title={task.status === 'COMPLETED' ? 'Mark incomplete' : 'Mark complete'} onClick={() => onToggle(task)}>{task.status === 'COMPLETED' && <Check size={14} />}</button><button className="task-main" onClick={() => onEdit(task)}><strong>{task.title}</strong><span>{task.subjectName || 'Personal'}{task.details ? ` · ${task.details}` : ''}</span></button><span className={`priority-tag priority-${(task.priority || 'medium').toLowerCase()}`}>{task.priority || 'MEDIUM'}</span>{!compact && task.status !== 'COMPLETED' && <span className="task-status">{task.status === 'IN_PROGRESS' ? 'In progress' : 'To do'}</span>}<span className={`task-date ${overdue ? 'date-overdue' : ''}`}>{dateLabel(task.dueDate)}</span>{onDelete && <button className="icon-button row-delete" title="Delete task" onClick={() => onDelete(task.id)}><Trash2 size={15} /></button>}</article>
}

function SubjectsPage({ subjects, onNew, onEdit, onDelete, onNavigate }) {
  return <><PageHeading eyebrow="A HOME FOR EVERY IDEA" title="Subjects" subtitle="Your curriculum, organized around you." action="Add subject" onAction={onNew} />{subjects.length ? <div className="subject-grid">{subjects.map((subject, index) => { const progress = subject.totalTasks ? Math.round(subject.completedTasks / subject.totalTasks * 100) : 0; return <article className="subject-card" key={subject.id} style={{ '--subject-color': subject.color, '--subject-delay': `${index * 50}ms` }}><div className="subject-card-top"><span className="subject-chip"><i /> STUDY AREA</span><button className="icon-button" title={`Edit ${subject.name}`} onClick={() => onEdit(subject)}><SquarePen size={16} /></button></div><h2>{subject.name}</h2><p>{subject.description || 'A good place for your next big idea.'}</p><div className="subject-progress-line"><span style={{ width: `${progress}%` }} /><strong>{progress}%</strong></div><div className="subject-card-foot"><span>{subject.completedTasks} of {subject.totalTasks} tasks done</span><button className="icon-button" title="Delete subject" onClick={() => onDelete(subject.id)}><Trash2 size={15} /></button></div><button className="subject-card-action" onClick={() => onNavigate('tasks')}>View tasks <ArrowRight size={15} /></button></article> })}<button className="subject-add-card" onClick={onNew}><span><Plus size={21} /></span><strong>Add another subject</strong><small>Keep every class in its place</small></button></div> : <section className="panel page-empty"><EmptyState icon={BookOpen} title="Every subject starts somewhere" copy="Create a subject, then collect your tasks and notes together." action="Add a subject" onClick={onNew} /></section>}</>
}

function NotesPage({ notes, onNew, onEdit, onDelete }) {
  return <><PageHeading eyebrow="KEEP THE GOOD BITS" title="Notes" subtitle="Little discoveries worth remembering." action="New note" onAction={onNew} />{notes.length ? <div className="notes-grid">{notes.map((note, index) => <article className={`note-card note-tone-${index % 4}`} key={note.id}><div className="note-card-top"><span>{note.subjectName || 'PERSONAL NOTE'}</span><button className="icon-button" title="Edit note" onClick={() => onEdit(note)}><SquarePen size={15} /></button></div><button className="note-content-button" onClick={() => onEdit(note)}><h2>{note.title}</h2><p>{note.content}</p></button><div className="note-card-foot"><span>{new Date(note.updatedAt || note.createdAt).toLocaleDateString('en', { month: 'short', day: 'numeric' })}</span><button className="icon-button" title="Delete note" onClick={() => onDelete(note.id)}><Trash2 size={14} /></button></div></article>)}</div> : <section className="panel page-empty"><EmptyState icon={FileText} title="A thought worth keeping" copy="Save a useful explanation, class note, or your own big idea." action="Create a note" onClick={onNew} /></section>}</>
}

function AssistantPage({ assistant, setAssistant, answer, busy, onAsk, plans, setPlans, savedPlans, onPlan }) {
  const [tab, setTab] = useState('assistant')
  return <><div className="welcome-row assistant-heading"><div><span className="eyebrow"><Sparkles size={13} /> A SECOND BRAIN FOR THE BIG STUFF</span><h1>Study, meet <em>smarter</em><span className="title-period">.</span></h1><p className="page-subtitle">A helpful thought partner, whenever you need one.</p></div></div><div className="assistant-tabs"><button className={tab === 'assistant' ? 'assistant-tab-active' : ''} onClick={() => setTab('assistant')}><FileText size={16} /> Ask a question</button><button className={tab === 'planner' ? 'assistant-tab-active' : ''} onClick={() => setTab('planner')}><Target size={16} /> Plan my week <small>AI</small></button></div>
    {tab === 'assistant' ? <div className="assistant-layout"><section className="assistant-main-panel"><div className="assistant-panel-head"><span className="assistant-avatar"><Sparkles size={17} /></span><div><strong>StudyMate assistant</strong><span><i /> Ready when you are</span></div><span className="assistant-powered">GEMINI</span></div><div className="assistant-intro"><span className="eyebrow">A FRESH PERSPECTIVE, IN SECONDS</span><h2>What are you working<br />through today?</h2><p>Ask about a tricky topic, get a clean summary, or untangle a question.</p></div><form className="assistant-form" onSubmit={onAsk}><div className="assistant-modes">{[['explain', 'Explain'], ['summarize', 'Summarize'], ['question', 'Answer a question'], ['tips', 'Study tips']].map(([id, label]) => <button type="button" className={assistant.mode === id ? 'mode-active' : ''} key={id} onClick={() => setAssistant({ ...assistant, mode: id })}>{label}</button>)}</div><textarea aria-label="Your study question" maxLength={6000} rows={4} placeholder="e.g. Explain how TCP congestion control works, with a simple analogy..." value={assistant.prompt} onChange={(event) => setAssistant({ ...assistant, prompt: event.target.value })} /><div className="assistant-form-bottom"><span>{assistant.prompt.length}/6000</span><button className="button button-primary" disabled={busy || !assistant.prompt.trim()}>{busy ? <LoaderCircle className="spin" size={16} /> : <Send size={15} />} Ask StudyMate</button></div></form>{answer && <div className="answer-block"><span className="answer-label"><Sparkles size={15} /> YOUR ANSWER</span><div className="answer-text">{answer}</div></div>}</section><aside className="assistant-aside"><span className="aside-orbit-icon"><Sparkles size={19} /></span><span className="eyebrow">THINK OUT LOUD</span><h3>Good questions make great study sessions.</h3><p>Start with one concept, then ask follow-ups until it clicks.</p><span className="suggestions-heading">A FEW PLACES TO START</span>{['Break this down for me', 'Give me a quick revision guide', 'Help me remember the key ideas'].map((suggestion) => <button className="suggestion-button" key={suggestion} onClick={() => setAssistant({ ...assistant, prompt: suggestion })}>{suggestion}<ArrowRight size={14} /></button>)}</aside></div> : <div className="planner-layout"><section className="panel planner-panel"><div className="planner-heading"><span className="planner-icon"><Target size={19} /></span><div><span className="eyebrow">MAKE YOUR TIME COUNT</span><h2>Build a study plan</h2></div></div><p className="planner-copy">We’ll shape your open tasks into a plan that leaves room to breathe. Your subjects, deadlines, and priorities are included automatically.</p><form className="planner-form" onSubmit={onPlan}><div className="form-two-col"><label>Start date<input type="date" required value={plans.startDate} onChange={(event) => setPlans({ ...plans, startDate: event.target.value })} /></label><label>Plan length<select value={plans.days} onChange={(event) => setPlans({ ...plans, days: event.target.value })}>{[3, 5, 7, 10, 14].map((days) => <option key={days} value={days}>{days} days</option>)}</select></label></div><label>Daily study time <span className="range-readout">{plans.hoursPerDay} hours / day</span><input type="range" min="1" max="8" value={plans.hoursPerDay} onChange={(event) => setPlans({ ...plans, hoursPerDay: event.target.value })} /></label><label>Your focus <span className="optional-label">OPTIONAL</span><input maxLength="500" placeholder="e.g. Midterms, catching up on lectures..." value={plans.focus} onChange={(event) => setPlans({ ...plans, focus: event.target.value })} /></label><button className="button button-primary planner-submit" disabled={busy}>{busy ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={16} />} Generate my plan <ArrowRight size={16} /></button></form></section><section className="panel plan-results"><div className="panel-heading"><div><span className="eyebrow">MADE FOR YOUR WEEK</span><h2>Your plans</h2></div><span className="plan-count">{savedPlans.length}</span></div>{savedPlans.length ? savedPlans.map((plan) => <article className="saved-plan" key={plan.id}><div className="saved-plan-date">{dateLabel(plan.startDate)} <ArrowRight size={13} /> {dateLabel(plan.endDate)}</div><p>{plan.planText}</p><small>Created {new Date(plan.createdAt).toLocaleDateString()}</small></article>) : <EmptyState icon={Target} title="Your week, with a little more shape" copy="Create your first plan. StudyMate will take your actual subjects and tasks into account." />}</section></div>}</>
}

function RecordModal({ modal, setModal, subjects, busy, onSubmit, onDelete }) {
  const { type, value } = modal
  const update = (key, next) => setModal({ ...modal, value: { ...value, [key]: next } })
  const edit = Boolean(value.id)
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setModal(null) }}><section className="record-modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div className="modal-head"><div><span className="eyebrow">YOUR STUDY DESK</span><h2 id="modal-title">{edit ? 'Edit' : 'New'} {type}<span className="title-period">.</span></h2></div><button className="icon-button" title="Close" onClick={() => setModal(null)}><X size={19} /></button></div><form className="record-form" onSubmit={onSubmit}>
    {type === 'subject' && <><label>Subject name<input autoFocus required maxLength={100} placeholder="e.g. Operating Systems" value={value.name || ''} onChange={(event) => update('name', event.target.value)} /></label><label>What are you studying? <small className="optional-label">OPTIONAL</small><textarea maxLength={500} rows={3} placeholder="A few words about this subject..." value={value.description || ''} onChange={(event) => update('description', event.target.value)} /></label><fieldset className="color-field"><legend>Pick a color</legend><div className="color-options">{COLORS.map((color) => <button type="button" key={color} className={`color-swatch ${value.color === color ? 'swatch-selected' : ''}`} style={{ '--swatch': color }} aria-label={`Select ${color}`} onClick={() => update('color', color)}>{value.color === color && <Check size={14} />}</button>)}</div></fieldset></>}
    {type === 'task' && <><label>What needs doing?<input autoFocus required maxLength={160} placeholder="e.g. Review lecture notes, chapter 4" value={value.title || ''} onChange={(event) => update('title', event.target.value)} /></label><label>Details <small className="optional-label">OPTIONAL</small><textarea rows={2} maxLength={1000} placeholder="Break it into a smaller step..." value={value.details || ''} onChange={(event) => update('details', event.target.value)} /></label><div className="form-two-col"><label>Subject<select value={value.subjectId || ''} onChange={(event) => update('subjectId', event.target.value)}><option value="">Personal / unassigned</option>{subjects.map((subject) => <option key={subject.id} value={subject.id}>{subject.name}</option>)}</select></label><label>Priority<select value={value.priority || 'MEDIUM'} onChange={(event) => update('priority', event.target.value)}><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option></select></label></div><div className="form-two-col"><label>Due date<input type="date" value={value.dueDate || ''} onChange={(event) => update('dueDate', event.target.value)} /></label><label>Status<select value={value.status || 'TODO'} onChange={(event) => update('status', event.target.value)}><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option></select></label></div></>}
    {type === 'note' && <><label>Note title<input autoFocus required maxLength={160} placeholder="Give this thought a name" value={value.title || ''} onChange={(event) => update('title', event.target.value)} /></label><label>Subject <small className="optional-label">OPTIONAL</small><select value={value.subjectId || ''} onChange={(event) => update('subjectId', event.target.value)}><option value="">Personal / unassigned</option>{subjects.map((subject) => <option key={subject.id} value={subject.id}>{subject.name}</option>)}</select></label><label>Your note<textarea required rows={8} maxLength={20000} placeholder="Write it down while it’s fresh..." value={value.content || ''} onChange={(event) => update('content', event.target.value)} /></label></>}
    <div className="modal-actions">{edit && <button type="button" className="icon-button modal-delete" title={`Delete ${type}`} onClick={() => { onDelete(type, value.id); setModal(null) }}><Trash2 size={17} /></button>}<button type="button" className="button button-quiet" onClick={() => setModal(null)}>Cancel</button><button className="button button-primary" disabled={busy}>{busy ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />}{edit ? 'Save changes' : `Create ${type}`}</button></div>
  </form></section></div>
}