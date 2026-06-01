import { EditorView, basicSetup } from "https://esm.sh/@codemirror/basic-setup@0.20";
import { java }    from "https://esm.sh/@codemirror/lang-java@6";
import { python }  from "https://esm.sh/@codemirror/lang-python@6";
import { oneDark } from "https://esm.sh/@codemirror/theme-one-dark@6";

const lang = document.body.dataset.language === 'python' ? python() : java();

const editor = new EditorView({
    doc: document.getElementById('initial-content').textContent,
    extensions: [basicSetup, lang, oneDark, EditorView.editable.of(false)],
    parent: document.getElementById('editor-mount')
});

document.querySelectorAll('.file-tree-item').forEach(item => {
    item.addEventListener('click', () => {
        editor.dispatch({
            changes: { from: 0, to: editor.state.doc.length, insert: item.dataset.content }
        });
        document.querySelectorAll('.file-tree-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
        document.getElementById('active-path').textContent = item.dataset.path;
    });
});
