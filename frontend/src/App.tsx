import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { clearUser, loadUser } from './auth/session'
import { NewProjectPage } from './pages/NewProjectPage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { ProjectListPage } from './pages/ProjectListPage'
import { SignInPage } from './pages/SignInPage'

export default function App() {
  const user = loadUser()
  const location = useLocation()

  if (!user && location.pathname !== '/') return <Navigate to="/" replace />

  return (
    <>
      {user && <Nav />}
      <Routes>
        <Route path="/" element={user ? <Navigate to="/projects" replace /> : <SignInPage />} />
        <Route path="/projects" element={<ProjectListPage />} />
        <Route path="/projects/new" element={<NewProjectPage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Routes>
    </>
  )
}

function Nav() {
  const navigate = useNavigate()
  const user = loadUser()
  if (!user) return null
  const initials = user.name
    .split(' ')
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <nav className="nav">
      <div className="nav-inner">
        <button
          type="button"
          className="nav-brand btn-ghost"
          onClick={() => navigate('/projects')}
          aria-label="Go to your projects"
        >
          <img src="/logo.png" alt="Gradion" className="logo" />
        </button>
        <button
          type="button"
          className="nav-brand btn-ghost"
          onClick={() => navigate('/projects')}
          aria-label="Go to your projects"
        >
          <span className="nav-title">Projects</span>
        </button>
        <div className="nav-user">
          <span className="avatar" aria-hidden="true">
            {initials}
          </span>
          {user.name}
          <button
            type="button"
            className="btn-ghost"
            style={{ color: 'var(--fg-3)', fontSize: 12 }}
            onClick={() => {
              clearUser()
              navigate('/', { replace: true })
            }}
          >
            Sign out
          </button>
        </div>
      </div>
    </nav>
  )
}
