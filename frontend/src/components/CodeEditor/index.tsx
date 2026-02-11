import React, { useRef, useEffect, forwardRef, useImperativeHandle } from 'react';
import * as monaco from 'monaco-editor';
import Editor, { loader } from '@monaco-editor/react';

export interface IProps {
  value?: string;
  defaultValue?: string;
  onChange?: (v: string) => void;
  extraOptions?: any;
  editorHeight?: string;
  defaultLanguage?: string;
}

export interface CodeEditorRef {
  pushContent: (content: string) => void;
}

const CodeEditor = forwardRef((props: IProps, ref) => {
  const { value, defaultValue, onChange, extraOptions, editorHeight, defaultLanguage } = props;
  loader.config({ monaco });

  const editorRef = useRef<any>(null);

  function handleEditorChange(val?: string) {
    onChange && onChange(val || '');
  }

  // 保存 editor 实例
  const handleEditorDidMount = (editor: any) => {
    editorRef.current = editor;
  };

  // value 或 defaultValue 变化时，手动 setValue
  useEffect(() => {
    const targetValue = value !== undefined ? value : defaultValue;
    if (editorRef.current && typeof targetValue === 'string') {
      // 只有内容不同时才 setValue，避免光标跳动
      if (editorRef.current.getValue() !== targetValue) {
        editorRef.current.setValue(targetValue);
      }
    }
  }, [value, defaultValue]);

  useImperativeHandle(ref, () => {
    return {
      pushContent: (content: string) => {
        const editor = editorRef.current;
        if (!editor) {
          return;
        }
        editor.executeEdits('', [{
          range: editor.getModel().getFullModelRange(),
          text: content,
        }]);
      },
    };
  });

  return (
    <div className="editor-container">
      <Editor
        height={editorHeight || '370px'}
        defaultLanguage={defaultLanguage || 'yaml'}
        value={value}
        defaultValue={defaultValue}
        options={{
          minimap: {
            enabled: false,
          },
          ...extraOptions,
        }}
        onMount={handleEditorDidMount}
        onChange={(val) => {
          handleEditorChange(val);
        }}
      />
    </div>
  );
});

export default CodeEditor;
