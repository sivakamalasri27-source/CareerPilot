import React, { useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { resumeApi } from '../api/resumeApi';
import { useToast } from '../context/ToastContext';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import {
  UploadCloud,
  FileText,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  Trash2,
  History,
  Lock,
} from 'lucide-react';
import { ResumeUploadResponse } from '../types/resume';
import { ApiError } from '../types/common';

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

export const ResumeUploadPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadResult, setUploadResult] = useState<ResumeUploadResponse | null>(null);

  // Queries
  const { data: history, isLoading: isHistoryLoading } = useQuery({
    queryKey: ['resume-history'],
    queryFn: resumeApi.getResumeHistory,
  });

  // Upload Mutation
  const uploadMutation = useMutation({
    mutationFn: (file: File) =>
      resumeApi.uploadResume(file, (percent) => setUploadProgress(percent)),
    onSuccess: (data) => {
      setUploadResult(data);
      setSelectedFile(null);
      setUploadProgress(0);
      queryClient.invalidateQueries({ queryKey: ['resume-history'] });
      queryClient.invalidateQueries({ queryKey: ['suggestions'] });
      showToast({
        type: 'success',
        title: 'Resume Processed Successfully',
        message: `Extracted ${data.suggestionsCreatedCount} suggestions for review in your Suggestions Inbox.`,
      });
    },
    onError: (err: ApiError) => {
      setUploadProgress(0);
      setClientError(err.message || 'Failed to upload and parse resume PDF.');
      showToast({
        type: 'error',
        title: 'Upload Failed',
        message: err.message || 'Could not process PDF resume.',
      });
    },
  });

  // Delete History Mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => resumeApi.deleteResume(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resume-history'] });
      showToast({ type: 'info', message: 'Resume record deleted.' });
    },
  });

  const validateAndSetFile = (file: File) => {
    setClientError(null);
    setUploadResult(null);

    // Validate type (must be PDF)
    const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
    if (!isPdf) {
      setClientError('Invalid file format. Only PDF files are supported.');
      return;
    }

    // Validate size (max 5 MB)
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setClientError(
        `File size exceeds 5 MB limit. Selected file size: ${(file.size / (1024 * 1024)).toFixed(2)} MB.`
      );
      return;
    }

    setSelectedFile(file);
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      validateAndSetFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      validateAndSetFile(e.target.files[0]);
    }
  };

  const handleUpload = () => {
    if (selectedFile) {
      uploadMutation.mutate(selectedFile);
    }
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <UploadCloud className="w-7 h-7 text-indigo-600" />
            Resume PDF Ingestion
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Upload your resume PDF for instant, in-memory parsing into structured suggestions.
          </p>
        </div>

        <div className="flex items-center gap-2 text-xs text-slate-500 bg-slate-100 px-3 py-1.5 rounded-xl">
          <Lock className="w-3.5 h-3.5 text-indigo-600" />
          <span>In-Memory Only (Zero Disk Retention)</span>
        </div>
      </div>

      {/* Drag & Drop Upload Zone */}
      <div className="bg-white rounded-2xl border border-slate-200 p-8 shadow-xs space-y-6">
        <div
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          className={`border-2 border-dashed rounded-2xl p-10 text-center cursor-pointer transition-all duration-200 ${
            dragActive
              ? 'border-indigo-500 bg-indigo-50/50 scale-[1.01]'
              : 'border-slate-300 hover:border-indigo-400 hover:bg-slate-50/50'
          }`}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,application/pdf"
            onChange={handleFileChange}
            className="hidden"
          />

          <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shadow-xs">
            <UploadCloud className="w-8 h-8" />
          </div>

          <h3 className="text-base font-bold text-slate-900">
            Drag & drop your resume PDF here
          </h3>
          <p className="mt-1 text-xs text-slate-500">
            Or click to browse from your device (PDF only, up to 5 MB)
          </p>

          {selectedFile && (
            <div className="mt-6 inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-50 border border-indigo-200 text-indigo-900 text-xs font-semibold">
              <FileText className="w-4 h-4 text-indigo-600" />
              <span>{selectedFile.name}</span>
              <span className="text-indigo-400">
                ({(selectedFile.size / (1024 * 1024)).toFixed(2)} MB)
              </span>
            </div>
          )}
        </div>

        {/* Client Validation Error */}
        {clientError && (
          <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-xs font-medium flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-red-600 flex-shrink-0" />
            <span>{clientError}</span>
          </div>
        )}

        {/* Upload Progress Bar */}
        {uploadMutation.isPending && (
          <div className="space-y-2">
            <div className="flex justify-between text-xs font-semibold text-slate-700">
              <span>Uploading & Parsing Resume...</span>
              <span>{uploadProgress}%</span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
              <div
                className="bg-indigo-600 h-2.5 rounded-full transition-all duration-300"
                style={{ width: `${uploadProgress}%` }}
              />
            </div>
          </div>
        )}

        {/* Upload Button */}
        {selectedFile && !uploadMutation.isPending && (
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setSelectedFile(null)}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleUpload}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 shadow-md shadow-indigo-100 transition"
            >
              <UploadCloud className="w-4 h-4" />
              Upload & Extract Profile Data
            </button>
          </div>
        )}

        {/* Successful Upload Result Banner & Link to Suggestions */}
        {uploadResult && (
          <div className="p-6 rounded-2xl bg-emerald-50 border border-emerald-200 space-y-4 animate-slide-in">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
                  <CheckCircle2 className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-emerald-950">
                    Resume Ingested Successfully
                  </h3>
                  <p className="text-xs text-emerald-800 mt-0.5">
                    Extracted {uploadResult.extractedTextLength} text characters and created{' '}
                    <span className="font-bold">{uploadResult.suggestionsCreatedCount} suggestions</span>.
                  </p>
                </div>
              </div>
            </div>

            <div className="pt-2 flex justify-end">
              <Link
                to="/suggestions"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white bg-emerald-700 hover:bg-emerald-800 shadow-sm transition"
              >
                Go to Suggestions Inbox to Review Items
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        )}
      </div>

      {/* Upload History Table */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs space-y-4">
        <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <History className="w-4 h-4 text-indigo-600" />
          Past Resume Uploads
        </h2>

        {isHistoryLoading ? (
          <CardSkeleton count={2} />
        ) : !history || history.length === 0 ? (
          <p className="text-xs text-slate-500 italic py-4">No past resume upload records found.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {history.map((item) => (
              <div key={item.id} className="py-3 flex items-center justify-between text-xs">
                <div className="space-y-0.5">
                  <span className="font-semibold text-slate-800">{item.fileName}</span>
                  <div className="flex items-center gap-2 text-[11px] text-slate-400">
                    <span>{(item.fileSizeBytes / (1024 * 1024)).toFixed(2)} MB</span>
                    <span>•</span>
                    <span>{new Date(item.createdAt).toLocaleDateString()}</span>
                    <span>•</span>
                    <span className="text-indigo-600 font-medium">
                      {item.suggestionsCreatedCount} suggestions
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => deleteMutation.mutate(item.id)}
                  className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition"
                  title="Delete resume record"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
