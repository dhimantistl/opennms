import { FileEditorResponseLog } from '@/types'

export interface State {
  fileNames: string[]
  file: string
  snippets: string
  searchValue: string
  contentModified: boolean
  selectedFileName: string
  modifiedFileString: string
  logs: FileEditorResponseLog[]
  isConsoleOpen: boolean
  isHelpOpen: boolean
}

const state: State = {
  fileNames: [],
  file: '',
  snippets: '',
  searchValue: '',
  contentModified: false,
  selectedFileName: '',
  modifiedFileString: '',
  logs: [],
  isConsoleOpen: false,
  isHelpOpen: false
}

export default state