import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import ts from 'typescript'
import { expect, test } from 'vitest'

const SOURCE_ROOT = join(__dirname, '..', '..')

const sourceFiles = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return entry.name === '__tests__' ? [] : sourceFiles(path)
    return path.endsWith('.tsx') && !path.includes('.test.') ? [path] : []
  })

const sizeAttribute = (attributes: ts.JsxAttributes): string => {
  const size = attributes.properties.find(
    (attribute) => ts.isJsxAttribute(attribute) && attribute.name.getText() === 'size',
  )
  if (!size || !ts.isJsxAttribute(size)) return 'missing'
  return size.initializer && ts.isStringLiteral(size.initializer)
    ? size.initializer.text
    : (size.initializer?.getText() ?? 'missing')
}

test('sets size="md" on every Carbon Button', () => {
  // Carbon defaults to lg (48px); TAPS buttons are md (40px) everywhere.
  const findings = sourceFiles(SOURCE_ROOT).flatMap((file) => {
    const sourceFile = ts.createSourceFile(
      file,
      readFileSync(file, 'utf8'),
      ts.ScriptTarget.Latest,
      true,
    )
    const fileFindings: string[] = []
    const visit = (node: ts.Node) => {
      if (
        (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) &&
        node.tagName.getText() === 'Button'
      ) {
        const size = sizeAttribute(node.attributes)
        if (size !== 'md') {
          const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart())
          fileFindings.push(`${relative(SOURCE_ROOT, file)}:${line + 1} size=${size}`)
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(sourceFile)
    return fileFindings
  })
  expect(findings).toEqual([])
})
