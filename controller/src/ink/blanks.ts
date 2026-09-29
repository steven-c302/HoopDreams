/** Splits the server's "_ _ _   _ _" (or "I _ _   _ _") into words of cells: '_' is a blank, anything else a shown letter. */
export function blankCells(blanks: string): string[][] {
  if (!blanks) return []
  return blanks.split('   ').map((word) => word.split(' ').filter((c) => c !== ''))
}
