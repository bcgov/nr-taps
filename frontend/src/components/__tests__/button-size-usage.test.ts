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

test('uses 40px application buttons with only the expressive 48px login exception', () => {
  // Only the two login actions use the verified expressive 48px layout; other actions are 40px.
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
        const label = ts.isJsxOpeningElement(node)
          ? node.parent.children
              .filter(ts.isJsxText)
              .map((child) => child.text.trim())
              .join('')
          : ''
        const loginAction =
          relative(SOURCE_ROOT, file) === join('components', 'Landing.tsx') &&
          ['Log in with IDIR', 'Log in with Business BCeID'].includes(label)
        const expressive = node.attributes.properties.some(
          (attribute) =>
            ts.isJsxAttribute(attribute) &&
            attribute.name.getText() === 'isExpressive' &&
            !attribute.initializer,
        )
        if (loginAction ? size !== 'lg' || !expressive : size !== 'md') {
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
