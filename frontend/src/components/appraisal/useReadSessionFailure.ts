import { useEffect } from 'react'
import { useAuth } from '@/context/auth/AuthContext'
import { ReadApiError } from '@/service/read-service'

export default function useReadSessionFailure(error?: Error) {
  const { reloadSession } = useAuth()
  useEffect(() => {
    if (error instanceof ReadApiError && error.status === 401) void reloadSession()
  }, [error, reloadSession])
}
