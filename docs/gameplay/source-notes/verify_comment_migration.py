from pathlib import Path
import hashlib
import json
import re


LEXEME = re.compile(
    r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    r'|//[^\r\n]*|/\*[\s\S]*?\*/'
    r'|>>>=|>>=|<<=|>>>|>>|<<|->|::|\+\+|--|&&|\|\||==|!=|<=|>='
    r'|\+=|-=|\*=|/=|%=|&=|\|=|\^=|\w+|[^\s]'
)


def code_digest(source):
    tokens = [match.group() for match in LEXEME.finditer(source)
              if not match.group().startswith(('//', '/*'))]
    data = json.dumps(tokens, ensure_ascii=False, separators=(',', ':')).encode()
    return hashlib.sha256(data).hexdigest()


def main():
    notes = Path(__file__).resolve().parent
    root = notes.parents[2]
    manifest = json.loads((notes / 'comment-migration.json').read_text())
    for entry in manifest['files']:
        path = root / entry['file']
        actual = code_digest(path.read_text())
        if actual != entry['code_sha256']:
            raise SystemExit(f'FAIL executable tokens changed: {entry["file"]}')
    print(f'PASS executable tokens unchanged in {len(manifest["files"])} comment-only files')


if __name__ == '__main__':
    main()
